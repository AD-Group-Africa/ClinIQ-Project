// =============================================================
// ClinIQ — WhatsApp Chatbot Service
// Runtime: Node.js 20 LTS
// Port: 5000
// =============================================================
// WHY THIS EXISTS:
//   Clinics lose 30-40% of potential bookings because patients
//   can't reach a receptionist. This chatbot handles bookings,
//   FAQs, reminders and appointment management 24/7 on WhatsApp
//   — the platform 95% of Kenyan patients already use.
//
// ARCHITECTURE:
//   - Stateless per-message processing
//   - Session state stored in Redis (conversation context)
//   - Intent matching is keyword-based (no AI cost)
//   - Escalation to staff via in-app notification
//   - Booking creates real appointments via REST API
// =============================================================

// package.json dependencies:
// {
//   "dependencies": {
//     "express": "^4.19.2",
//     "axios": "^1.7.2",
//     "redis": "^4.6.14",
//     "express-rate-limit": "^7.3.1",
//     "helmet": "^7.1.0",
//     "morgan": "^1.10.0",
//     "winston": "^3.13.0"
//   }
// }


'use strict';
const express = require('express');
const axios = require('axios');
const { createClient: createRedisClient } = require('redis');
const rateLimit = require('express-rate-limit');
const helmet = require('helmet');
const morgan = require('morgan');
const { createLogger, format, transports } = require('winston');

// =============================================
// Logger
// =============================================
const log = createLogger({
  level: process.env.LOG_LEVEL || 'info',
  format: format.combine(
    format.timestamp(),
    format.errors({ stack: true }),
    format.json()
  ),
  transports: [
    new transports.Console(),
    new transports.File({ filename: '/var/log/cliniq/chatbot-error.log', level: 'error' }),
    new transports.File({ filename: '/var/log/cliniq/chatbot.log' })
  ]
});

// =============================================
// Redis client (session/context storage)
// =============================================
const redis = createRedisClient({
  url: process.env.REDIS_URL || 'redis://localhost:6379',
  password: process.env.REDIS_PASSWORD || undefined
});
redis.connect().then(() => log.info('Redis connected'))
  .catch(err => log.error('Redis connection failed', err));


// =============================================
// ClinIQ API client
// =============================================
const cliniqApi = axios.create({
  baseURL: process.env.CLINIQ_API_URL || 'http://localhost:8080',
  timeout: 8000,
  headers: {
    'Content-Type': 'application/json',
    // Internal service key — set on Spring Boot side via application.yaml
    'X-Service-Key': process.env.INTERNAL_SERVICE_KEY || ''
  }
});


// =============================================
// WhatsApp API client (Meta Cloud API)
// =============================================
const waApi = axios.create({
  baseURL: `https://graph.facebook.com/v19.0/${process.env.WHATSAPP_PHONE_ID}`,
  headers: {
    'Authorization': `Bearer ${process.env.WHATSAPP_ACCESS_TOKEN}`,
    'Content-Type': 'application/json'
  }
});

async function sendWhatsApp(to, body, type = 'text') {
  try {
    await waApi.post('/messages', {
      messaging_product: 'whatsapp',
      to: normalizePhone(to),
      type: 'text',
      text: { body, preview_url: false }
    });
  } catch (err) {
    log.error('WhatsApp send failed', { to, error: err.message });
  }
}

async function sendWhatsAppButtons(to, bodyText, buttons) {
  try {
    await waApi.post('/messages', {
      messaging_product: 'whatsapp',
      to: normalizePhone(to),
      type: 'interactive',
      interactive: {
        type: 'button',
        body: { text: bodyText },
        action: {
          buttons: buttons.map((btn, i) => ({
            type: 'reply',
            reply: { id: `btn_${i}`, title: btn }
          }))
        }
      }
    });
  } catch (err) {
    // Fallback to plain text if buttons fail
    await sendWhatsApp(to, bodyText + '\n\n' + buttons.map((b, i) => `${i + 1}. ${b}`).join('\n'));
  }
}


// =============================================
// Session management (Redis, 30-minute TTL)
// =============================================
const SESSION_TTL = 1800; // 30 minutes

async function getSession(phone, clinicSlug) {
  const key = `chatbot:session:${clinicSlug}:${phone}`;
  const data = await redis.get(key);
  return data ? JSON.parse(data) : {
    phone,
    clinicSlug,
    step: 'IDLE',
    booking: {},
    patientId: null,
    language: 'en',
    createdAt: Date.now()
  };
}

async function saveSession(phone, clinicSlug, session) {
  const key = `chatbot:session:${clinicSlug}:${phone}`;
  await redis.setEx(key, SESSION_TTL, JSON.stringify(session));
}

async function clearSession(phone, clinicSlug) {
  await redis.del(`chatbot:session:${clinicSlug}:${phone}`);
}


// =============================================
// Intent matcher (keyword-based, no AI cost)
// =============================================
const INTENTS = [
  { name: 'BOOK',      patterns: [/\b(book|appoint|schedule|see.*doctor|visit)\b/i] },
  { name: 'HOURS',     patterns: [/\b(hour|open|time|when|close|operating)\b/i] },
  { name: 'LOCATION',  patterns: [/\b(where|location|address|direction|map|find you)\b/i] },
  { name: 'SERVICES',  patterns: [/\b(service|offer|treat|speciali|do you|provide)\b/i] },
  { name: 'CANCEL',    patterns: [/\b(cancel|reschedule|change.*appoint)\b/i] },
  { name: 'RESULTS',   patterns: [/\b(result|lab|test.*result|my result)\b/i] },
  { name: 'HUMAN',     patterns: [/\b(human|agent|staff|person|talk|speak|receptionist|help)\b/i] },
  { name: 'GREETING',  patterns: [/^(hi|hello|hey|hujambo|habari|sawa|uko|niaje|mambo|good\s*(morning|afternoon|evening))$/i] },
  { name: 'CONFIRM',   patterns: [/^(yes|yeah|yep|ok|okay|sure|confirm|ndio|sawa|correct)$/i] },
  { name: 'CANCEL_FLOW', patterns: [/^(no|nope|cancel|stop|exit|quit|back|hapana)$/i] },
  { name: 'MENU',      patterns: [/^(menu|start|restart|main|back to menu|\d)$/i] },
];

function matchIntent(text) {
  const clean = text.trim().toLowerCase();
  for (const intent of INTENTS) {
    if (intent.patterns.some(p => p.test(clean))) return intent.name;
  }
  // Number selections (1-5 menu picks)
  if (/^[1-5]$/.test(clean)) return `SELECT_${clean}`;
  return 'UNKNOWN';
}


// =============================================
// Clinic info cache (reduces API calls)
// =============================================
async function getClinicInfo(clinicSlug) {
  const cacheKey = `clinic:info:${clinicSlug}`;
  const cached = await redis.get(cacheKey);
  if (cached) return JSON.parse(cached);

  try {
    const { data } = await cliniqApi.get(`/api/v1/public/clinic/${clinicSlug}`);
    const info = data.data;
    await redis.setEx(cacheKey, 3600, JSON.stringify(info)); // cache 1 hour
    return info;
  } catch {
    return { name: 'The Clinic', phone: '', address: '', hours: 'Mon–Fri 8am–6pm', services: [] };
  }
}

async function getDoctors(clinicSlug) {
  const cacheKey = `clinic:doctors:${clinicSlug}`;
  const cached = await redis.get(cacheKey);
  if (cached) return JSON.parse(cached);

  try {
    const { data } = await cliniqApi.get(`/api/v1/public/clinic/${clinicSlug}/doctors`);
    await redis.setEx(cacheKey, 3600, JSON.stringify(data.data));
    return data.data;
  } catch {
    return [];
  }
}


// =============================================
// Message processor (state machine)
// =============================================
async function processMessage({ phone, text, clinicSlug }) {
  const session = await getSession(phone, clinicSlug);
  const clinic = await getClinicInfo(clinicSlug);
  const intent = matchIntent(text);

  log.info('Processing message', { phone: phone.slice(-4), step: session.step, intent });

  // --- ACTIVE BOOKING FLOW ---
  if (session.step.startsWith('BOOKING_')) {
    return await handleBookingFlow(phone, text, intent, session, clinic, clinicSlug);
  }

  // --- ACTIVE CANCELLATION FLOW ---
  if (session.step === 'CANCEL_PHONE') {
    return await handleCancellationFlow(phone, text, session, clinic, clinicSlug);
  }

  // --- ESCALATED (waiting for staff) ---
  if (session.step === 'ESCALATED') {
    await saveSession(phone, clinicSlug, { ...session, lastMessage: text });
    return { message: `✋ Your message has been received. A staff member from ${clinic.name} will respond shortly.` };
  }

  // --- MAIN MENU ROUTING ---
  switch (intent) {
    case 'GREETING':
      return await sendWelcome(phone, clinic, session, clinicSlug);

    case 'SELECT_1':
    case 'BOOK':
      return await startBookingFlow(phone, session, clinic, clinicSlug);

    case 'SELECT_2':
    case 'HOURS':
      return {
        message: `🕐 *${clinic.name} opening hours:*\n\n${clinic.hours || 'Mon–Fri: 8:00 AM – 6:00 PM\nSaturday: 9:00 AM – 2:00 PM\nSunday: Closed'}\n\n_Reply with "menu" to go back._`
      };

    case 'SELECT_3':
    case 'LOCATION':
      return {
        message: `📍 *Find us here:*\n\n${clinic.address || 'Please call us for directions.'}\n\n📞 ${clinic.phone || 'Contact clinic'}\n\n_Reply with "menu" to go back._`
      };

    case 'SELECT_4':
    case 'SERVICES': {
      const services = (clinic.services || []).join(', ') || 'General consultation, dental, lab, pharmacy';
      return { message: `🏥 *Our services:*\n\n${services}\n\n_Reply with "menu" to go back._` };
    }

    case 'SELECT_5':
    case 'CANCEL':
      return await startCancellationFlow(phone, session, clinicSlug);

    case 'RESULTS':
      return {
        message: `🔬 *Lab results:*\n\nTo access your results, please visit ${clinic.name} in person or call us at ${clinic.phone || 'our reception'}.\n\nFor privacy and accuracy, results are shared directly with patients.`
      };

    case 'HUMAN':
      return await escalateToHuman(phone, session, clinic, clinicSlug);

    case 'MENU':
    case 'CANCEL_FLOW':
      await clearSession(phone, clinicSlug);
      return await sendWelcome(phone, clinic, { ...session, step: 'IDLE' }, clinicSlug);

    default:
      return await sendWelcome(phone, clinic, session, clinicSlug);
  }
}


// =============================================
// Welcome message
// =============================================
async function sendWelcome(phone, clinic, session, clinicSlug) {
  await saveSession(phone, clinicSlug, { ...session, step: 'MENU' });
  return {
    message: `👋 Hello! Welcome to *${clinic.name}*.\n\nI'm your virtual assistant. How can I help you today?\n\n1️⃣ Book an appointment\n2️⃣ Our opening hours\n3️⃣ Our location\n4️⃣ Our services\n5️⃣ Cancel/reschedule\n\nOr type *"staff"* to speak with our team.`,
    buttons: ['Book appointment', 'Opening hours', 'Location']
  };
}


// =============================================
// Booking flow (5-step state machine)
// =============================================
async function startBookingFlow(phone, session, clinic, clinicSlug) {
  const doctors = await getDoctors(clinicSlug);

  if (!doctors.length) {
    return { message: `I'm sorry, online booking isn't available right now. Please call ${clinic.phone || 'us'} to book.` };
  }

  const doctorList = doctors.slice(0, 5).map((d, i) =>
    `${i + 1}. Dr. ${d.firstName} ${d.lastName} — ${d.specialization || 'General'}`
  ).join('\n');

  await saveSession(phone, clinicSlug, {
    ...session,
    step: 'BOOKING_SELECT_DOCTOR',
    booking: { doctors: doctors.slice(0, 5) }
  });

  return {
    message: `📅 *Book an appointment*\n\nPlease select a doctor:\n\n${doctorList}\n\nReply with the number (1–${doctors.slice(0,5).length}).`
  };
}

async function handleBookingFlow(phone, text, intent, session, clinic, clinicSlug) {
  const num = parseInt(text.trim());

  switch (session.step) {
    case 'BOOKING_SELECT_DOCTOR': {
      const doctors = session.booking.doctors || [];
      if (!num || num < 1 || num > doctors.length) {
        return { message: `Please reply with a number between 1 and ${doctors.length}.` };
      }
      const doctor = doctors[num - 1];

      // Get available slots for the next 3 days
      const slots = await getAvailableSlots(clinicSlug, doctor.id);
      if (!slots.length) {
        return { message: `Dr. ${doctor.firstName} ${doctor.lastName} has no available slots in the next 3 days. Please call ${clinic.phone || 'us'} to check availability.` };
      }

      const slotList = slots.slice(0, 6).map((s, i) =>
        `${i + 1}. ${s.dateLabel} at ${s.time}`
      ).join('\n');

      await saveSession(phone, clinicSlug, {
        ...session,
        step: 'BOOKING_SELECT_SLOT',
        booking: { ...session.booking, doctor, slots: slots.slice(0, 6) }
      });

      return {
        message: `👨‍⚕️ *Dr. ${doctor.firstName} ${doctor.lastName}*\n${doctor.specialization || 'General Practice'}\n\nAvailable slots:\n\n${slotList}\n\nReply with a number to select your preferred time.`
      };
    }

    case 'BOOKING_SELECT_SLOT': {
      const slots = session.booking.slots || [];
      if (!num || num < 1 || num > slots.length) {
        return { message: `Please reply with a number between 1 and ${slots.length}.` };
      }
      const slot = slots[num - 1];

      await saveSession(phone, clinicSlug, {
        ...session,
        step: 'BOOKING_GET_NAME',
        booking: { ...session.booking, slot }
      });

      return {
        message: `✅ You selected:\n📅 ${slot.dateLabel} at ${slot.time}\n\nPlease type your *full name* to continue.`
      };
    }

    case 'BOOKING_GET_NAME': {
      const name = text.trim();
      if (name.length < 3) {
        return { message: 'Please enter your full name (at least 3 characters).' };
      }

      await saveSession(phone, clinicSlug, {
        ...session,
        step: 'BOOKING_GET_COMPLAINT',
        booking: { ...session.booking, patientName: name }
      });

      return {
        message: `Thank you, *${name}*! 😊\n\nBriefly describe the reason for your visit.\n(e.g. "Headache and fever", "Follow-up visit", "General checkup")`
      };
    }

    case 'BOOKING_GET_COMPLAINT': {
      const complaint = text.trim();
      const { doctor, slot, patientName } = session.booking;

      // Confirm before creating
      await saveSession(phone, clinicSlug, {
        ...session,
        step: 'BOOKING_CONFIRM',
        booking: { ...session.booking, complaint }
      });

      return {
        message: `📋 *Please confirm your appointment:*\n\n👤 Name: ${patientName}\n👨‍⚕️ Doctor: Dr. ${doctor.firstName} ${doctor.lastName}\n📅 Date: ${slot.dateLabel}\n⏰ Time: ${slot.time}\n💬 Reason: ${complaint}\n\nReply *YES* to confirm or *NO* to cancel.`
      };
    }

    case 'BOOKING_CONFIRM': {
      if (intent === 'CONFIRM') {
        return await createAppointment(phone, session, clinic, clinicSlug);
      } else if (intent === 'CANCEL_FLOW') {
        await clearSession(phone, clinicSlug);
        return { message: `Booking cancelled. Reply "menu" to start again or call ${clinic.phone || 'us'} for assistance.` };
      }
      return { message: 'Please reply *YES* to confirm your booking or *NO* to cancel.' };
    }

    default:
      return await sendWelcome(phone, clinic, session, clinicSlug);
  }
}

async function createAppointment(phone, session, clinic, clinicSlug) {
  const { doctor, slot, patientName, complaint } = session.booking;

  try {
    const response = await cliniqApi.post(`/api/v1/public/clinic/${clinicSlug}/appointments`, {
      patientPhone: normalizePhone(phone),
      patientName,
      doctorId: doctor.id,
      appointmentDate: slot.date,
      startTime: slot.startTime,
      chiefComplaint: complaint,
      bookedVia: 'WHATSAPP'
    });

    const appt = response.data.data;
    await clearSession(phone, clinicSlug);

    return {
      message: `🎉 *Booking confirmed!*\n\nHi ${patientName}, your appointment has been booked.\n\n📅 ${slot.dateLabel} at ${slot.time}\n👨‍⚕️ Dr. ${doctor.firstName} ${doctor.lastName}\n🔢 Queue number: *${appt.queueNumber || 'TBC'}*\n\nPlease arrive *10 minutes early* and bring your ID.\n\n_${clinic.name}_ · ${clinic.phone || ''}`
    };
  } catch (err) {
    log.error('Appointment creation failed', { phone: phone.slice(-4), error: err.message });
    await clearSession(phone, clinicSlug);
    return {
      message: `Sorry, we couldn't book your appointment automatically. 😔\n\nPlease call ${clinic.phone || 'us'} directly to complete your booking.\n\nApologies for the inconvenience.`
    };
  }
}


// =============================================
// Cancellation flow
// =============================================
async function startCancellationFlow(phone, session, clinicSlug) {
  await saveSession(phone, clinicSlug, { ...session, step: 'CANCEL_PHONE' });
  return {
    message: `To cancel your appointment, please confirm your phone number.\n\nReply with your registered phone number (e.g. 0712345678).`
  };
}

async function handleCancellationFlow(phone, text, session, clinic, clinicSlug) {
  const inputPhone = normalizePhone(text.trim());
  try {
    await cliniqApi.post(`/api/v1/public/clinic/${session.clinicSlug}/appointments/cancel-by-phone`, {
      phone: inputPhone
    });
    await clearSession(phone, clinicSlug);
    return { message: `✅ Your upcoming appointment has been cancelled.\n\nTo book again, reply "book".\n\nThank you — ${clinic.name}` };
  } catch {
    await clearSession(phone, clinicSlug);
    return { message: `We couldn't find an upcoming appointment for that number. Please call ${clinic.phone || 'us'} for assistance.` };
  }
}


// =============================================
// Human escalation
// =============================================
async function escalateToHuman(phone, session, clinic, clinicSlug) {
  await saveSession(phone, clinicSlug, { ...session, step: 'ESCALATED' });

  // Notify clinic via internal API
  try {
    await cliniqApi.post(`/api/v1/public/clinic/${clinicSlug}/chatbot/escalate`, {
      phone: normalizePhone(phone),
      channel: 'WHATSAPP',
      lastMessage: session.lastMessage || '(no message)'
    });
  } catch (err) {
    log.warn('Escalation notification failed', err.message);
  }

  return {
    message: `✋ I've notified our team that you need assistance.\n\nA staff member from *${clinic.name}* will respond shortly during office hours.\n\nFor urgent matters, please call: ${clinic.phone || 'our reception directly'}.`
  };
}


// =============================================
// Slot availability helper
// =============================================
async function getAvailableSlots(clinicSlug, doctorId) {
  try {
    const today = new Date();
    const slots = [];

    for (let d = 0; d <= 3; d++) {
      const date = new Date(today);
      date.setDate(today.getDate() + d);
      const dateStr = date.toISOString().split('T')[0];

      const { data } = await cliniqApi.get(
        `/api/v1/public/clinic/${clinicSlug}/schedule/slots?doctorId=${doctorId}&date=${dateStr}`
      );

      (data.data || []).forEach(slot => {
        slots.push({
          date: dateStr,
          startTime: slot.startTime,
          time: formatTime(slot.startTime),
          dateLabel: formatDateLabel(date, d)
        });
      });
    }
    return slots;
  } catch {
    return [];
  }
}

function formatDateLabel(date, daysFromNow) {
  if (daysFromNow === 0) return 'Today';
  if (daysFromNow === 1) return 'Tomorrow';
  return date.toLocaleDateString('en-KE', { weekday: 'long', month: 'short', day: 'numeric' });
}

function formatTime(time24) {
  const [h, m] = time24.split(':').map(Number);
  const period = h >= 12 ? 'PM' : 'AM';
  const h12 = h % 12 || 12;
  return `${h12}:${m.toString().padStart(2, '0')} ${period}`;
}

function normalizePhone(phone) {
  const clean = phone.replace(/\D/g, '');
  if (clean.startsWith('254')) return `+${clean}`;
  if (clean.startsWith('07') || clean.startsWith('01')) return `+254${clean.slice(1)}`;
  if (clean.startsWith('7') || clean.startsWith('1')) return `+254${clean}`;
  return `+${clean}`;
}


// =============================================
// Web chat handler (website widget)
// =============================================
async function handleWebChat(req, res) {
  const { sessionId, message, clinicSlug } = req.body;
  if (!message || !clinicSlug) return res.status(400).json({ error: 'Missing fields' });

  const response = await processMessage({
    phone: `web_${sessionId}`,
    text: message,
    clinicSlug
  });

  res.json({ message: response.message, buttons: response.buttons || [] });
}


// =============================================
// Express app
// =============================================
const app = express();

app.use(helmet());
app.use(express.json({ limit: '1mb' }));
app.use(morgan('combined', { stream: { write: msg => log.info(msg.trim()) } }));

// Rate limiting
app.use('/webhook', rateLimit({ windowMs: 60_000, max: 100, message: 'Too many requests' }));
app.use('/chat', rateLimit({ windowMs: 60_000, max: 60, message: 'Too many requests' }));

// Health check
app.get('/health', (_, res) => res.json({ status: 'ok', service: 'cliniq-chatbot' }));

// WhatsApp verification
app.get('/webhook/whatsapp', (req, res) => {
  const mode = req.query['hub.mode'];
  const token = req.query['hub.verify_token'];
  const challenge = req.query['hub.challenge'];

  if (mode === 'subscribe' && token === process.env.WHATSAPP_VERIFY_TOKEN) {
    log.info('WhatsApp webhook verified');
    return res.status(200).send(challenge);
  }
  res.sendStatus(403);
});

// WhatsApp incoming messages
app.post('/webhook/whatsapp', async (req, res) => {
  res.sendStatus(200); // Acknowledge immediately (Meta requires < 5s response)

  try {
    const entry = req.body.entry?.[0];
    const changes = entry?.changes?.[0];
    const messages = changes?.value?.messages;

    if (!messages?.length) return;

    for (const msg of messages) {
      const phone = msg.from;
      const text = msg.text?.body || msg.interactive?.button_reply?.title || '';
      if (!text) continue;

      // Determine clinic from phone number mapping or default
      const clinicSlug = await getClinicSlugFromPhone(phone) || process.env.DEFAULT_CLINIC_SLUG || 'demo';

      const response = await processMessage({ phone, text, clinicSlug });

      if (response.buttons && response.buttons.length) {
        await sendWhatsAppButtons(phone, response.message, response.buttons);
      } else {
        await sendWhatsApp(phone, response.message);
      }
    }
  } catch (err) {
    log.error('Webhook processing error', err);
  }
});

// Web chat endpoint
app.post('/chat', handleWebChat);

// Clinic slug from phone mapping (Redis-backed)
async function getClinicSlugFromPhone(phone) {
  // Clinics register their WhatsApp number → this maps inbound numbers to clinic slugs
  // In production, each clinic has its own WhatsApp Business number
  return process.env.DEFAULT_CLINIC_SLUG || 'demo';
}

// Start server
const PORT = process.env.PORT || 5000;
app.listen(PORT, () => {
  log.info(`ClinIQ chatbot service running on port ${PORT}`);
});

module.exports = app;
