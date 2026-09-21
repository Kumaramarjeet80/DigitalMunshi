package com.digitalmunshi.pos.domain.usecases

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.digitalmunshi.pos.core.qr.DynamicUpiQrGenerator
import com.digitalmunshi.pos.data.local.entities.KhataCustomerEntity
import com.digitalmunshi.pos.data.local.entities.TransactionEntity
import java.net.URLEncoder
import java.util.Locale

data class KhataReminderData(
    val formattedMessage: String,
    val smsIntent: Intent,
    val whatsAppIntent: Intent
)

object KhataReminderGenerator {

    /**
     * Builds offline pre-filled reminder intents for SMS and WhatsApp.
     * Incorporates outstanding balance, invoice reference, and compliant merchant UPI deep-link.
     */
    fun createReminder(
        customer: KhataCustomerEntity,
        storeName: String,
        merchantVpa: String,
        merchantName: String,
        lastTransaction: TransactionEntity? = null
    ): KhataReminderData {
        val upiPaymentLink = DynamicUpiQrGenerator.buildUpiUri(
            payeeVpa = merchantVpa,
            payeeName = merchantName,
            amount = customer.currentBalance,
            transactionRef = "KHATA_${customer.id}_${System.currentTimeMillis()}",
            transactionNote = "Khata Clearance for ${customer.name}"
        )

        val invoiceSnippet = if (lastTransaction != null) {
            "Recent Invoice: #${lastTransaction.invoiceNo} (Rs. ${String.format(Locale.US, "%.2f", lastTransaction.grandTotal)})\n"
        } else {
            ""
        }

        val message = """
            Namaste ${customer.name},
            
            This is a gentle reminder from $storeName regarding your outstanding store balance.
            
            Current Balance Due: Rs. ${String.format(Locale.US, "%.2f", customer.currentBalance)}
            $invoiceSnippet
            You can clear your dues directly by tapping this UPI payment link:
            $upiPaymentLink
            
            Thank you for your valued patronage!
        """.trimIndent()

        val encodedMessage = URLEncoder.encode(message, "UTF-8")

        // 1. Native SMS Intent (Works completely offline via SIM card)
        val smsUri = Uri.parse("smsto:${customer.phone}")
        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // 2. WhatsApp Direct Intent (Transfers text payload to local WhatsApp app)
        val cleanPhone = customer.phone.replace("+", "").replace(" ", "").replace("-", "")
        val formattedPhone = if (cleanPhone.length == 10) "91$cleanPhone" else cleanPhone
        val whatsAppUri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=$encodedMessage")
        val whatsAppIntent = Intent(Intent.ACTION_VIEW, whatsAppUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return KhataReminderData(
            formattedMessage = message,
            smsIntent = smsIntent,
            whatsAppIntent = whatsAppIntent
        )
    }
}
