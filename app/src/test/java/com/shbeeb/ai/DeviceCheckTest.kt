package com.shbeeb.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCheckTest {
    @Test fun reportExplainsAnUninstalledModelIsNotWorking() {
        val report = DeviceCheck(
            androidApi = 35,
            device = "Android Test Phone",
            memoryTotalMb = 8000,
            memoryFreeMb = 3000,
            storageFreeMb = 12000,
            importedModelMb = null,
            supportsVideoEncoder = false
        ).toArabicReport()
        assertTrue(report.contains("نموذج الهاتف: غير مستورد"))
        assertTrue(report.contains("لم تُفحص بالاستدلال الفعلي"))
        assertTrue(report.contains("لم نعثر على مشفّر"))
    }

    @Test fun reportDoesNotEquateFileImportWithValidInference() {
        val report = DeviceCheck(
            androidApi = 35,
            device = "Android Test Phone",
            memoryTotalMb = 8000,
            memoryFreeMb = 2500,
            storageFreeMb = 10000,
            importedModelMb = 780,
            supportsVideoEncoder = true
        ).toArabicReport()
        assertTrue(report.contains("ملف مستورد حجمه 780"))
        assertTrue(report.contains("لم يُختبر تشغيله"))
        assertTrue(report.contains("مشفّر يدعم الإعدادات المطلوبة مبدئياً"))
        assertFalse(report.contains("مفتاح API: "))
    }
}
