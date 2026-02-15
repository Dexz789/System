package com.example.strawberry2.Otp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object EmailService {
    private const val TAG = "EmailService"

    // Email configuration - IMPORTANT: Use app-specific password for Gmail
    private const val SMTP_HOST = "smtp.gmail.com"
    private const val SMTP_PORT = "587"
    private const val EMAIL_FROM = "growmate.project@gmail.com"
    private const val EMAIL_PASSWORD = "ccmn kclp hasb grjg"

    /**
     * Send OTP email for registration
     */
    fun sendOtpEmail(
        email: String,
        otp: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        sendEmail(
            email = email,
            subject = "Your Verification Code - GrowMate Registration",
            htmlContent = getRegistrationEmailHtmlContent(otp),
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    /**
     * Send OTP email for login
     */
    fun sendLoginOtpEmail(
        email: String,
        otp: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        sendEmail(
            email = email,
            subject = "Your Login Verification Code - GrowMate",
            htmlContent = getLoginEmailHtmlContent(otp),
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    /**
     * Generic email sending function
     */
    private fun sendEmail(
        email: String,
        subject: String,
        htmlContent: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        Thread {
            try {
                val properties = Properties().apply {
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.host", SMTP_HOST)
                    put("mail.smtp.port", SMTP_PORT)
                    put("mail.smtp.ssl.protocols", "TLSv1.2")
                }

                val session = Session.getInstance(properties, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(EMAIL_FROM))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
                    this.subject = subject
                    setContent(htmlContent, "text/html; charset=utf-8")
                }

                Transport.send(message)
                Log.d(TAG, "Email sent successfully to $email")
                onSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to send email", e)
                onFailure(e.message ?: "Unknown error")
            }
        }.start()
    }

    /**
     * Generate HTML content for registration OTP email
     */
    private fun getRegistrationEmailHtmlContent(otp: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background-color: #f4f7f6;
                        margin: 0;
                        padding: 0;
                    }
                    .container {
                        max-width: 600px;
                        margin: 40px auto;
                        background-color: #ffffff;
                        border-radius: 12px;
                        overflow: hidden;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                    }
                    .header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        padding: 40px 20px;
                        text-align: center;
                        color: white;
                    }
                    .header h1 {
                        margin: 0;
                        font-size: 28px;
                        font-weight: 600;
                    }
                    .content {
                        padding: 40px 30px;
                        text-align: center;
                    }
                    .content p {
                        color: #555;
                        font-size: 16px;
                        line-height: 1.6;
                        margin-bottom: 30px;
                    }
                    .otp-box {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        font-size: 36px;
                        font-weight: bold;
                        padding: 20px 40px;
                        border-radius: 10px;
                        letter-spacing: 8px;
                        display: inline-block;
                        margin: 20px 0;
                        box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
                    }
                    .warning {
                        background-color: #fff3cd;
                        border-left: 4px solid #ffc107;
                        padding: 15px;
                        margin: 30px 0;
                        border-radius: 5px;
                        text-align: left;
                    }
                    .warning p {
                        margin: 0;
                        color: #856404;
                        font-size: 14px;
                    }
                    .footer {
                        background-color: #f8f9fa;
                        padding: 20px;
                        text-align: center;
                        color: #6c757d;
                        font-size: 14px;
                        border-top: 1px solid #e9ecef;
                    }
                    .footer a {
                        color: #667eea;
                        text-decoration: none;
                    }
                    .icon {
                        font-size: 48px;
                        margin-bottom: 20px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="icon">🌿</div>
                        <h1>GrowMate Registration</h1>
                    </div>
                    <div class="content">
                        <h2 style="color: #333; margin-bottom: 10px;">Verify Your Email Address</h2>
                        <p>Thank you for registering with GrowMate! To complete your registration, please use the verification code below:</p>
                        
                        <div class="otp-box">$otp</div>
                        
                        <p>This code will expire in <strong>5 minutes</strong>.</p>
                        
                        <div class="warning">
                            <p>⚠️ <strong>Security Notice:</strong> Never share this code with anyone. GrowMate will never ask for your verification code via email or phone.</p>
                        </div>
                        
                        <p style="margin-top: 30px; font-size: 14px; color: #888;">
                            If you didn't request this code, please ignore this email or contact our support team.
                        </p>
                    </div>
                    <div class="footer">
                        <p>© 2025 GrowMate Inc. All Rights Reserved.</p>
                        <p>Need help? <a href="mailto:support@growmate.com">Contact Support</a></p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generate HTML content for login OTP email
     */
    private fun getLoginEmailHtmlContent(otp: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background-color: #f4f7f6;
                        margin: 0;
                        padding: 0;
                    }
                    .container {
                        max-width: 600px;
                        margin: 40px auto;
                        background-color: #ffffff;
                        border-radius: 12px;
                        overflow: hidden;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                    }
                    .header {
                        background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
                        padding: 40px 20px;
                        text-align: center;
                        color: white;
                    }
                    .header h1 {
                        margin: 0;
                        font-size: 28px;
                        font-weight: 600;
                    }
                    .content {
                        padding: 40px 30px;
                        text-align: center;
                    }
                    .content p {
                        color: #555;
                        font-size: 16px;
                        line-height: 1.6;
                        margin-bottom: 30px;
                    }
                    .otp-box {
                        background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
                        color: white;
                        font-size: 36px;
                        font-weight: bold;
                        padding: 20px 40px;
                        border-radius: 10px;
                        letter-spacing: 8px;
                        display: inline-block;
                        margin: 20px 0;
                        box-shadow: 0 4px 12px rgba(67, 233, 123, 0.3);
                    }
                    .warning {
                        background-color: #fff3cd;
                        border-left: 4px solid #ffc107;
                        padding: 15px;
                        margin: 30px 0;
                        border-radius: 5px;
                        text-align: left;
                    }
                    .warning p {
                        margin: 0;
                        color: #856404;
                        font-size: 14px;
                    }
                    .security-notice {
                        background-color: #d1ecf1;
                        border-left: 4px solid #0c5460;
                        padding: 15px;
                        margin: 20px 0;
                        border-radius: 5px;
                        text-align: left;
                    }
                    .security-notice p {
                        margin: 0;
                        color: #0c5460;
                        font-size: 14px;
                    }
                    .footer {
                        background-color: #f8f9fa;
                        padding: 20px;
                        text-align: center;
                        color: #6c757d;
                        font-size: 14px;
                        border-top: 1px solid #e9ecef;
                    }
                    .footer a {
                        color: #43e97b;
                        text-decoration: none;
                    }
                    .icon {
                        font-size: 48px;
                        margin-bottom: 20px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="icon">🔐</div>
                        <h1>GrowMate Login Verification</h1>
                    </div>
                    <div class="content">
                        <h2 style="color: #333; margin-bottom: 10px;">Verify Your Login</h2>
                        <p>We detected a login attempt to your GrowMate account. To complete your login, please use the verification code below:</p>
                        
                        <div class="otp-box">$otp</div>
                        
                        <p>This code will expire in <strong>5 minutes</strong>.</p>
                        
                        <div class="security-notice">
                            <p>📍 <strong>Login Activity:</strong> If you didn't attempt to log in, please secure your account immediately by changing your password.</p>
                        </div>
                        
                        <div class="warning">
                            <p>⚠️ <strong>Security Notice:</strong> Never share this code with anyone. GrowMate will never ask for your verification code via email or phone.</p>
                        </div>
                        
                        <p style="margin-top: 30px; font-size: 14px; color: #888;">
                            If you didn't request this code, please ignore this email and consider changing your password.
                        </p>
                    </div>
                    <div class="footer">
                        <p>© 2025 GrowMate Inc. All Rights Reserved.</p>
                        <p>Need help? <a href="mailto:support@growmate.com">Contact Support</a></p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Suspend function version for coroutines
     */
    suspend fun sendOtpEmailAsync(email: String, otp: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            suspendCoroutine { continuation ->
                sendOtpEmail(
                    email = email,
                    otp = otp,
                    onSuccess = { continuation.resume(Result.success(Unit)) },
                    onFailure = { error -> continuation.resume(Result.failure(Exception(error))) }
                )
            }
        }
}