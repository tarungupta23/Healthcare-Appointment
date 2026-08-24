import { Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';

import LandingPage from './pages/LandingPage';
import DoctorSearchPage from './pages/DoctorSearchPage';
import DoctorBookingPage from './pages/DoctorBookingPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';

import PatientDashboard from './pages/patient/PatientDashboard';

import DoctorDashboard from './pages/doctor/DoctorDashboard';
import DoctorAppointmentDetail from './pages/doctor/DoctorAppointmentDetail';

import AdminDashboard from './pages/admin/AdminDashboard';
import AddDoctorPage from './pages/admin/AddDoctorPage';
import DoctorLeavePage from './pages/admin/DoctorLeavePage';

export default function App() {
  return (
    <AuthProvider>
      <Navbar />
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/doctors" element={<DoctorSearchPage />} />
        <Route path="/doctors/:doctorId" element={<DoctorBookingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route path="/patient" element={<ProtectedRoute role="PATIENT"><PatientDashboard /></ProtectedRoute>} />

        <Route path="/doctor" element={<ProtectedRoute role="DOCTOR"><DoctorDashboard /></ProtectedRoute>} />
        <Route path="/doctor/appointments/:appointmentId" element={<ProtectedRoute role="DOCTOR"><DoctorAppointmentDetail /></ProtectedRoute>} />

        <Route path="/admin" element={<ProtectedRoute role="ADMIN"><AdminDashboard /></ProtectedRoute>} />
        <Route path="/admin/doctors/new" element={<ProtectedRoute role="ADMIN"><AddDoctorPage /></ProtectedRoute>} />
        <Route path="/admin/doctors/:doctorId/leave" element={<ProtectedRoute role="ADMIN"><DoctorLeavePage /></ProtectedRoute>} />

        <Route path="*" element={<LandingPage />} />
      </Routes>
    </AuthProvider>
  );
}
