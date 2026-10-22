# TV Kapat

Android TV box için otomatik kapatma zamanlayıcısı.

- Süre (dk) veya saat seç → BAŞLAT → uygulama kapanır, servis arka planda çalışır.
- Bitmeden 30 sn önce ekranda geri sayım çıkar (Vazgeç / 15 dk ertele / Geri tuşu = iptal).
- Süre dolunca cihaz kapatılır: önce root (`reboot -p`), olmazsa erişilebilirlik servisi güç menüsünden "Kapat"a basar.

## İzinleri ADB ile verme (TV'de ayar ekranı yoksa)
    adb shell appops set com.mesut.tvkapat SYSTEM_ALERT_WINDOW allow
    adb shell settings put secure enabled_accessibility_services com.mesut.tvkapat/com.mesut.tvkapat.PowerAccessibilityService
    adb shell settings put secure accessibility_enabled 1
