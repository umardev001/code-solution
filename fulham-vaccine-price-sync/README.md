# Fulham Vaccine Price Sync (WordPress plugin)

Fulham Travel Clinic (`fulhamtravelclinic.com/all-vaccinations/`) ki prices ko **Fulham Wellness** ki saari pages par ek click mein update karta hai.
Har change ka record rakhta hai (purani price, nayi price, kis page par, kis ne, kab) aur ek click se undo bhi hota hai.

## Install
1. `fulham-vaccine-price-sync.zip` ko **Plugins → Add New → Upload Plugin** se install karein (client ki site `fulhamwellness.co.uk`) aur activate karein.
2. Left menu mein **Vaccine Price Sync** khulega.

## Use
1. **Sync** tab: source se prices load hoti hain (32 vaccines). Zaroorat ho to *Refresh*.
2. **Scan all pages & review changes**: saare pages scan hote hain. Review tab mein har page ke liye `purani → nayi` price dikhti hai. Jo update nahi karna uska tick hata dein, phir *Apply selected changes*.
   - Ya seedha **Update ALL prices now** (one click, bina review ke).
3. **History & rollback**: har run ka record. Poora run *Undo whole run* se, ya ek price *Restore £xx* se wapas. CSV download bhi hai.
4. **Settings & mappings**: jin vaccine names ka naam dono sites par alag hai (e.g. `Rabies prophylaxis = Rabies (pre-exposure)`) unki mapping yahan likhein. Optional daily auto-sync bhi.

## Kya detect hota hai
- Table rows (`prices-travel-vaccine`): pehla cell vaccine ka naam, price wala cell `£85/dose`.
- `Disease: Hepatitis A … Price: £70/dose` wali lists (`/vaccination/*` pages).
- Jin pages par sirf `Price: £95` likha ho, wahan page ka title (e.g. "Private Flu") vaccine maana jata hai (Review mein "page title" likha aata hai).
- Sirf `£` ke baad ka number badalta hai; `/dose`, `/Course` jaisa text jyun ka tyun rehta hai.
- Elementor (`_elementor_data`) aur normal post content, dono.

## Safety
- Pehle preview, phir apply. Page scan ke baad edit ho jaye to wo price skip hoti hai.
- Update se pehle WordPress revision save hoti hai; Elementor cache clear hoti hai.
- Jo vaccine source list mein nahi (e.g. "Rabies post-exposure treatment") "no match" dikhta hai aur kabhi touch nahi hota.

## Source ki zaroorat
Source page par har price ki row mein CSS classes `ftc-pr-row`, `ftc-pr-name`, `ftc-pr-price`, `ftc-pr-unit` hon (Travel Clinic ke Elementor loop mein hain). Layout badla to `includes/class-fvps-engine.php` ka `parse_source()` adjust karein.

## Tests (optional)
```
php tests/run-engine-test.php source.html page1.html page2.html
php -d zend.assertions=1 -d assert.exception=1 tests/roundtrip-test.php source.html
```
