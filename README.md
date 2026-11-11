# RaceAuraBridge

Menyinkronkan max health **RaceCore** dengan stat Health **AuraSkills**.

RaceCore mengubah base MAX_HEALTH (ganti ras, siang/malam). AuraSkills hanya
menghitung ulang HP saat join/ganti world/stat berubah. Plugin ini mendeteksi
perubahan base dan memicu reload HP AuraSkills.

## Build lewat GitHub
1. Buat repo baru di GitHub, upload semua isi folder ini.
2. Buka tab **Actions** -> workflow **Build** (jalan otomatis saat push).
3. Setelah hijau, buka run-nya, download artifact **RaceAuraBridge** (isinya jar).
4. Taruh jar di `plugins/`, restart server.

## Pemakaian
- `/raceaura sync` : paksa sinkronisasi semua pemain (izin `raceaura.admin`).
- `config.yml` : interval cek dan delay setelah join.

Rekomendasi di `plugins/AuraSkills/stats.yml` bagian `auraskills/hp`:
`health_scaling: false` dan `force_base_health: false`.

Versi ini dibuat untuk Paper 26.2 (Java 25). Untuk server 1.21.x, ubah `paper.version` jadi `1.21.4-R0.1-SNAPSHOT`, Java ke 21, dan `api-version` ke `1.21`.
