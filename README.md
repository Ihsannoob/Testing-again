# AICerebrasPlugin (Paper)

Plugin Paper sederhana untuk integrasi dengan API Cerebras (Llama 3.3 70B).
Fitur:
- /ai chat <message> : Kirim message ke model, jawabannya dikirim ke pemain.
- /ai view : Buka GUI untuk melihat history percakapan (klik untuk melihat konten penuh).
- /ai export : Export percakapan pemain ke file JSON di plugins/AICerebras/exports/.
- /ai clear : Hapus history pemain (menghapus file SQLite per-player).
- /ai reload : Reload konfigurasi (permission ai.reload atau OP).

Struktur penyimpanan:
- Percakapan tiap pemain disimpan di SQLite file per-player: `plugins/AICerebras/players/<uuid>.db`
- Export disimpan di: `plugins/AICerebras/exports/<uuid>_<timestamp>.json`

Build
- Pastikan Java 17 terinstall.
- Jalankan:
  mvn clean package
- Hasil jar ada di `target/ai-cerebras-plugin-1.1.0-shaded.jar` (tergantung konfigurasi Maven).

Install
- Copy jar ke folder server `plugins/`.
- Jalankan server Paper.
- Edit `plugins/AICerebras/config.yml`:
  - Ganti `api_key` dengan API key Cerebras.
  - Ganti `endpoint` dengan endpoint resmi Cerebras untuk model Llama 3.3 70B jika berbeda.
- Gunakan `/ai chat Hello` di dalam game.

Catatan penting
- Contoh payload/respon di client dibuat generik. Cerebras bisa memiliki struktur respons khusus; sesuaikan parsing di `CerebrasClient.java` jika struktur berbeda.
- Hati-hati dengan rate limit dan kredensial API. Jangan commit API key ke public repo.
- Clear menghapus file database pemain — tidak dapat dikembalikan.
- Jika mau fitur tambahan (paging GUI, enkripsi export, admin export of others), saya bisa tambahkan.
