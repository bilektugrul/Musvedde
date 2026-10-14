# Web editörü — tasarım

Tarih: 2026-10-14 · Durum: inceleme bekliyor

## Amaç

Telefondaki Müsvedde, aynı Wi-Fi'daki bir bilgisayarın tarayıcısına not düzenleme arayüzü sunar.

Öncelik sırası:

1. **Hızlı ekleme.** Bilgisayarda Google Keep açıkken notları kopyala-yapıştır ile art arda Müsvedde'ye eklemek. Keep Takeout zip'ini tarayıcıdan yüklemek.
2. **Göz atma, arama, kopyalama.** Notları bilgisayardan okumak, anında aramak, panoya almak.
3. **Toplu işlemler.** Birden çok notu renklendirmek, etiketlemek, sabitlemek, arşivlemek, çöpe atmak.

Başarı ölçütleri:

- Kopyala, yapıştır, Ctrl+Enter, sıradaki not döngüsü telefona dokunmadan sürebilmeli.
- Binlerce notla arama ve kaydırma takılmamalı.
- Gizli klasördeki hiçbir not web'e ulaşmamalı.

## Kararlar (kullanıcıyla netleşenler)

- **Erişim:** Sadece aynı Wi-Fi ağı. İnternet üzerinden erişim ve aracı sunucu yok.
- **Ömür:** Uygulamada "Başlat" ile açılır, kullanıcı durdurana kadar çalışır. Zaman aşımı yok.
- **Eşleştirme:** Güvenilir bilgisayarlar. İlk bağlantıda kod girilir, sonra o tarayıcı hatırlanır. Ayarlardan kaldırılabilir.
- **Gizli klasör:** Web'de hiç görünmez. Web'den gizli klasöre not eklenemez ve taşınamaz.
- **QR kod:** Yok. Sadece adres gösterilir.
- **Bağlantı:** Düz HTTP. Ev ağı güvenilir kabul edilir. Kullanıcı bu riski kabul etti, uygulamada uyarı gösterilir.
- **Düzen:** Üç düzen birlikte desteklenir: ızgara, üç bölme, ızgara + yan panel.
- **Teknoloji:** Telefonda Ktor CIO sunucusu. Arayüz assets'e gömülü Preact, htm ve @preact/signals ile yazılır. Node ve JS derleme adımı yok.

## Mimari (telefon)

Yeni paket `dev.aliarslan.musvedde.web`. Mevcut katmanlar sadece public API'leri üzerinden kullanılır.

| Bileşen | Görev |
|---|---|
| `WebEditorService` | `connectedDevice` tipinde ön plan servisi. `Başlat` ile açılır, kullanıcı durdurana kadar çalışır. Bildirimde adres ve "Durdur" eylemi bulunur. Sunucu açıkken `WifiLock` (yüksek performans) tutar. |
| `WebServer` | Ktor CIO. Wi-Fi arayüzünün IPv4 adresine bağlanır, `0.0.0.0` kullanılmaz. Port 8080, doluysa 8090'a kadar sıradaki boş port. Statik dosyaları ve `/api/*` uçlarını sunar. gzip sıkıştırma açık. |
| `WebApi` | Route'lar. `NoteRepository`, `LabelRepository`, `VersionRepository`, `KeepImporter`, `RestoreService` ve `SettingsRepository`'yi çağırır. İş mantığı kopyalanmaz. |
| `WebAuth` | Eşleştirme kodu, deneme kısıtlaması, güvenilir bilgisayar anahtarları, çerez doğrulama, Host ve Origin kontrolleri. |
| `WebEvents` | Gizli olmayan notların ve ayarların Flow'larını dinler. Her değişimde önceki anlık görüntüyle karşılaştırıp değişen ve kaybolan not id'lerini artan bir `revision` numarasıyla bellekte kaydeder, SSE ile bildirir. Arşivleme, renk ya da gizli klasöre taşıma `updatedAt`'i değiştirmese de fark böylece yakalanır. |
| `WebEditorState` | Servis durumunu StateFlow ile arayüze verir: `Stopped`, `Starting`, `Running(address, pairingCode)`, `NoWifi`, `Failed(reason)`. |

Telefondaki arayüz:

- **Ayarlar > Web editörü** kategorisi:
  - Başlat/Durdur
  - Adres (dokununca kopyalanır)
  - Eşleştirme kodu (sadece burada görünür, bildirimde görünmez)
  - Eşleşmiş bilgisayarlar listesi: ad, son görülme, kaldır, tümünü kaldır
  - "Bağlantı şifrelenmez. Sadece güvendiğin ağlarda kullan." uyarısı
- **Çekmece:** "Web editörü" kısayolu, oturum açıkken yanında nokta göstergesi.

Veritabanı:

- Yeni `web_devices` tablosu: `id`, `name`, `tokenHash` (SHA-256, hex), `createdAt`, `lastSeenAt`.
- Room sürümü 5'ten 6'ya çıkar, `MIGRATION_5_6` eklenir ve şema JSON'u commit edilir.
- Yedeklere dahil edilmez, çünkü cihaza özgüdür.

Yeni bağımlılıklar: `ktor-server-cio`, `ktor-server-content-negotiation`, `ktor-serialization-kotlinx-json`, `ktor-server-compression`, `ktor-server-sse`. Bunlar version catalog'a eklenir.

Manifest izinleri: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`.

## Güvenlik

**Eşleştirme:**

- Her oturum başında 6 haneli rastgele bir kod üretilir (`SecureRandom`).
- Tanınmayan tarayıcı eşleştirme ekranında kodu ve bilgisayar adını girer. Ad, user-agent'tan tahmin edilip önceden doldurulur, örneğin "Chrome · Windows".
- Kod doğruysa 32 baytlık rastgele bir anahtar üretilir. Tarayıcıya `HttpOnly; SameSite=Strict; Max-Age=31536000; Path=/` çerezi olarak verilir. Telefonda sadece SHA-256 özeti saklanır.
- Her başarılı eşleştirmeden sonra kod yenilenir.
- 5 yanlış denemede kod yenilenir ve bekleme süresi başlar: 30 saniye, her seferinde iki katına çıkar. Telefondaki kilidin mantığıyla aynıdır ama sayaç ayrıdır.

**İstek güvenliği:**

- `/api/pair`, eşleştirme sayfası ve statik dosyalar dışındaki her istek çerez anahtarıyla doğrulanır. Doğrulamada eşleşen kaydın `lastSeenAt` alanı güncellenir, en fazla dakikada bir.
- `Host` başlığı, telefonun dinlediği `IP:port` olmak zorunda. Bu DNS rebinding'e karşı korur.
- Değişiklik yapan istekler (`POST`, `PATCH`, `DELETE`) için `Origin` başlığı aynı origin olmak zorunda. Bu CSRF'e karşı korur.
- Bir bilgisayar kaldırılınca anahtarı anında geçersiz olur ve açık SSE bağlantısı kapatılır.

**Gizli notlar:**

- Bütün sorgular `locked = 0` filtresiyle yapılır.
- Gizli bir notun id'siyle gelen her istek 404 döner.
- Toplu işlemlerde gizli id'ler sessizce atlanır.
- Notlar telefonda gizli klasöre taşınınca web'e "silindi" olarak bildirilir. Gizli klasörden çıkarılınca "eklendi" olarak bildirilir.

**Bilinen sınır:** Düz HTTP olduğu için aynı ağı dinleyen biri notları ve çerezi görebilir. Bu tasarımın kabul ettiği bir risktir ve kullanıcı uygulamada uyarılır.

## API

| Uç nokta | İş |
|---|---|
| `GET /`, `GET /assets/*` | Sayfa ve statik dosyalar. Önbellek ETag ile yapılır, dosya adresleri `versionCode` içerir. |
| `POST /api/pair` | `{code, name}` alır. 200 ile çerez, 403 (yanlış kod) ya da 429 (`retryAfter`) döner. |
| `GET /api/bootstrap` | Notlar (tam metin) ve etiketler. Tema renkleri: Monet dahil, telefonun ürettiği `ColorScheme`'in hex değerleri ve not renkleri. Dil metinleri, sıralama ve güncel `revision`. |
| `GET /api/notes?since=rev` | `rev`'den sonra değişen notlar ve kaybolan id'ler: `{notes, deletedIds, revision}`. `rev` oturumun günlüğünden eskiyse `410` döner ve tarayıcı `bootstrap`'ı yeniden yükler. |
| `POST /api/notes` | `{title, body, color, labelIds}` ile oluşturur ve oluşan notu döner. |
| `PATCH /api/notes/{id}` | `{title, body, baseUpdatedAt, force?}` alır. Not bu arada değiştiyse 409 ve güncel not döner. |
| `POST /api/notes/bulk` | `{ids, action, value?}` alır. `action` şunlardan biri: `color`, `addLabel`, `removeLabel`, `pin`, `unpin`, `archive`, `unarchive`, `trash`, `restore`, `deleteForever`, `emptyTrash`. |
| `POST`, `DELETE /api/notes/{id}/session` | `VersionRepository.beginSession` ve `endSession`. Web'deki düzenlemeler de not geçmişine girer. SSE bağlantısı kopunca açık oturumlar kapatılır. |
| `POST /api/labels`, `PATCH`/`DELETE /api/labels/{id}` | Etiket oluşturma, yeniden adlandırma, silme. |
| `POST /api/import/keep` | Takeout zip'ini akış olarak okur, 200 MB sınırı var. `KeepImporter` ve `RestoreService` (MERGE) kullanılır. `{added, skipped}` döner. |
| `GET /api/events` | SSE: `notes`, `labels`, `theme`, `bye` (oturum durdu) olayları ve 25 saniyede bir kalp atışı. |

JSON modelleri `web/WebModels.kt` dosyasında `@Serializable` olarak tanımlanır. Domain modelleri doğrudan dışarı açılmaz.

## Web arayüzü

Dosyalar `app/src/main/assets/web/` altında:

- `index.html`
- `app.js`: giriş noktası
- birkaç modül: `store.js`, `api.js`, `search.js`, `views/*.js`
- `app.css`
- `vendor/`: Preact, htm, signals'ın ESM dosyaları, sürümleri sabitlenmiş

Hepsi tarayıcının native ES modülleriyle yüklenir.

### Durum ve performans

- **Tek kaynak:** Açılışta `bootstrap` bir kez yüklenir. Notlar bellekte `Map<id, note>` olarak tutulur ve signals ile görünüme bağlanır.
- **Arama:** Bellekte yapılır. Her not için normalleştirilmiş metin bir kez hesaplanır: Türkçe küçük harf, aksanlar ve `ı/i` farkı kaldırılır. Her tuşta filtre çalışır, 150 ms'den uzun sürerse `requestIdleCallback` ile parçalanır. Eşleşmeler vurgulanır.
- **Sanal liste:** Sadece ekrana yakın kartlar ve satırlar DOM'a eklenir. Izgara düzeni için sütunlar JS ile hesaplanır, kart yükseklikleri ölçülüp önbelleğe alınır.
- **İyimser güncelleme:** İşlem önce arayüzde uygulanır, istek başarısız olursa geri alınır ve hata gösterilir.
- **Canlı güncelleme:** SSE olayı `revision` taşır; tarayıcı kendi revizyonundan yeniyse `since` ile sadece farkları çeker.
- **Akıcı geçişler:** Görünümler ve not açılışı View Transitions API ile yapılır, destek yoksa animasyonsuz çalışır. Diyaloglar ve menüler native `<dialog>` ve `popover` ile yapılır.

### Düzenler

Üst çubukta üç simgeli bir düğme ve Ctrl+1/2/3 kısayolları var. Seçim tarayıcının `localStorage`'ında saklanır, varsayılan A'dır.

- **A · Izgara:** Hızlı ekleme kutusu ve renkli kartlardan oluşan masonry ızgara. Not ortada büyüyen bir diyalogda açılır.
- **B · Üç bölme:** Kenar çubuğu, kompakt liste ve sürekli açık düzenleyici bölmesi.
- **C · Izgara + yan panel:** A'daki ızgara. Not sağdan açılan bir panelde düzenlenir, ızgara görünür kalır.

Kenar çubuğu (Notlar, Arşiv, Çöp kutusu, etiketler), düzenleyici bileşeni ve hızlı ekleme üç düzende ortaktır.

### Hızlı ekleme

- **A ve C'de:** Üstteki kutu odaklanınca başlık, gövde, renk ve etiket alanları açılır.
- **B'de:** Ctrl+N ya da "Yeni not" ile düzenleyici bölmesinde boş not açılır.
- **Ctrl+Enter:** Kaydeder ve yeni boş bir not açar.
- **Esc:** Kaydedip kapatır.
- Boş not kaydedilmez.
- Renk ve etiket seçimi oturum boyunca hatırlanır.
- **Başlık önerisi:** Yapıştırılan gövdenin ilk satırı 60 karakterden kısaysa ve ardından başka satırlar varsa "Başlık yap" çipi gösterilir. Otomatik taşıma yapılmaz.

### Göz atma, arama, kopyalama

- **Gezinme:** Kenar çubuğunda Notlar, Arşiv, Çöp kutusu ve etiketler var. Sıralama telefondakiyle aynı, sabitlenenler üstte.
- **Arama:** `/` ya da Ctrl+K arama kutusuna odaklanır.
- **Kopyalama:** Her notta "Kopyala" var, açık notta kısayolu Ctrl+Shift+C. `navigator.clipboard` kullanılamazsa, ki HTTP'de çoğu tarayıcıda kullanılamaz, gizli bir textarea ile `document.execCommand('copy')` yapılır.

### Düzenleme

- **Otomatik kayıt:** Yazmayı bıraktıktan 600 ms sonra `PATCH` gönderilir. Durum göstergesi "Kaydediliyor…" ya da "Kaydedildi" gösterir.
- **Not oturumu:** Not açılınca `session` başlatılır, kapanınca bitirilir.
- **409 gelirse** "Bu not telefonda değişti" şeridi çıkar:
  - "Benimkini tut" `force: true` ile yeniden gönderir.
  - "Telefondakini al" yerel metni sunucudakiyle değiştirir.
  - İki durumda da eski metin not geçmişinde kalır.
- **Web'de olmayanlar:** Gizli klasöre taşıma, bildirime sabitleme, sürükleyerek sıralama, not geçmişi görüntüleme.

### Toplu işlemler

- **Seçme:** Kart ya da satır köşesindeki onay kutusuyla. Shift+tık aralık seçer, Ctrl+A görünenlerin hepsini seçer, Esc seçimi bırakır.
- **Seçim çubuğu:** Renk, etiket ekle/çıkar, sabitle, arşivle, çöpe at, birleşik kopyala. Birleşik kopyalama notları aralarında boş satırla birleştirir.
- **Geri al:** Çöpe atma ve arşivleme sonrası 5 saniyelik "Geri al" bildirimi çıkar. Geri al, tersi olan toplu işlemi gönderir.

### Takeout

- Sayfaya zip sürükleyip bırakmak ya da menüden "Keep'ten içe aktar" seçmek yüklemeyi başlatır. İlerleme `XMLHttpRequest` upload olaylarıyla gösterilir.
- Sonuç bildirilir, örneğin "312 not eklendi, 4 not zaten vardı".

### Bağlantı ve oturum

- **SSE koparsa:** "Telefona bağlanılamıyor" şeridi çıkar. Tarayıcı yeniden bağlanır, sonra `since` ile farkı çeker.
- **Bekleyen kayıtlar:** Sırada tutulur, üstel beklemeyle yeniden denenir. Sekme kapatılırken bekleyen kayıt varsa `beforeunload` uyarısı çıkar.
- **401 gelirse:** Eşleştirme ekranına dönülür.
- **`bye` olayı gelirse:** "Oturum kapandı" ekranı gösterilir.

### Görünüm

- **Tema:** `bootstrap` ve `theme` olaylarıyla gelen renkler CSS değişkenlerine yazılır, telefondaki tema ve Monet ayarı birebir taşınır. Not renkleri telefondaki açık ve koyu tablolardan gelir.
- **Dil:** Uygulamanın dili kullanılır, metinler `bootstrap` ile gelir.
- **Ekran boyutu:** Tasarım masaüstü önceliklidir. Dar ekranda kenar çubuğu çekmeceye dönüşür.

## Hata yönetimi

| Durum | Davranış |
|---|---|
| Wi-Fi yok ya da değişti | Durum `NoWifi` olur. Ağ değişince sunucu yeni IP'ye yeniden bağlanır ve adres güncellenir. |
| 8080–8090 arası dolu | Durum `Failed`, uygulamada mesaj gösterilir. |
| Bozuk ya da büyük zip | 400 ya da 413 döner, "Dosya okunamadı" mesajı çıkar. Veritabanına hiçbir şey yazılmaz. |
| Beklenmeyen istisna | 500 döner, mevcut `ErrorLogger`'a yazılır ve Sorun giderme ekranında görünür. |
| Servisi sistem öldürdü | Bildirim kaybolur, durum `Stopped` olur. Otomatik yeniden başlatma yok (`START_NOT_STICKY`). |

## Testler

- **Ktor `testApplication` ve Robolectric:**
  - Eşleştirme: doğru kod, yanlış kod, kısıtlama, kod yenilenmesi.
  - Çerez doğrulama ve kaldırılan cihaz.
  - Host ve Origin reddi.
  - Gizli notların hiçbir uçtan sızmaması: bootstrap, since, PATCH, bulk, session, events.
  - 409 ve `force` akışı.
  - Toplu işlemler.
  - Takeout yükleme ve bozuk zip.
  - Revizyon günlüğü, `since` ve `410`.
- **Birim testleri:** `WebDeviceDao`, `MIGRATION_5_6`, kod üretimi ve kısıtlama.
- **Web arayüzü:** Otomatik JS testi yok. Uygulama bitince yerleşik tarayıcıyla uçtan uca elle doğrulanır:
  - eşleştirme
  - hızlı ekleme döngüsü
  - arama
  - üç düzen
  - toplu işlem ve geri al
  - çakışma şeridi
  - canlı güncelleme
  - Takeout yükleme
  - bağlantı kopması

## Kapsam dışı

HTTPS, web'de gizli klasör, QR kod, sürükleyerek sıralama, web'de not geçmişi, bildirime sabitleme, yedekleme ayarları, internet üzerinden erişim, birden fazla telefona bağlanma.
