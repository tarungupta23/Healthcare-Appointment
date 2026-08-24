# Meridian Clinic - Frontend

React + Vite single-page app for the Healthcare Appointment & Follow-up Manager.

## Setup

```bash
npm install
cp .env.example .env   # edit VITE_API_BASE_URL if the backend isn't on localhost:8080
npm run dev
```

Runs at http://localhost:5173 by default and expects the Spring Boot backend at
`VITE_API_BASE_URL` (default `http://localhost:8080/api`).

## Build

```bash
npm run build
```

Outputs static assets to `dist/`, deployable to Vercel/Netlify/any static host.
