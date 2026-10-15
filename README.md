# Premier Advocates – WordPress theme (Hello Elementor child)

`premier-advocates-child/` is a child theme of **Hello Elementor**. It contains the approved design
(Home, About Us, How We Can Help, Senior Executive Support, Book a Consultation, Contact) built from
**Elementor widgets**, with the **header and footer built as ElementsKit templates**. All styling lives in `style.css`.
`premier-advocates-child.zip` is ready to upload.

## Install
1. Install and activate the parent theme **Hello Elementor** (Appearance → Themes → Add New).
2. Appearance → Themes → Add New → Upload `premier-advocates-child.zip` → Activate.
3. On activation the theme automatically creates the pages, Primary Menu, ElementsKit header/footer templates and sets Home as the front page.
4. Go to **Appearance → Premier Setup** (an admin notice links there) and click **Install & activate** for **Elementor** and **ElementsKit Lite**.

## Notes
- Same homepage footer on every page; header menu = *Appearance → Menus → Primary Menu*. The mobile menu has a high `z-index` so it never overlaps page text.
- Edit header/footer: ElementsKit → Header & Footer (templates "Premier Header" / "Premier Footer"). Without the plugins the theme prints an identical static header/footer.
- Contact form works without plugins (recipient set in Premier Setup). The "Book this option" buttons link to the Contact form with the option pre-filled – replace with a Stripe/calendar link when available.
- Privacy / Terms / Cookies pages contain starter text that needs legal review.
- Speed: fonts self-hosted, icons are tiny CSS masks, images compressed, Google-font/emoji/block-CSS loading removed.
