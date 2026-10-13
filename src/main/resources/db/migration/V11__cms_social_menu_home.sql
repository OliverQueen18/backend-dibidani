insert into shop_settings (setting_key, setting_value, updated_at) values
  ('youtubeUrl', '', now()),
  ('linkedinUrl', '', now())
on conflict (setting_key) do nothing;

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'SOCIAL', 'whatsapp', 'WhatsApp', null, null, null, 'https://wa.me/22376123456', null, 'wa', 1, true
where not exists (select 1 from site_blocks where kind = 'SOCIAL' and slug = 'whatsapp');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'SOCIAL', 'facebook', 'Facebook', null, null, null, 'https://www.facebook.com/dibidani', null, 'fb', 2, true
where not exists (select 1 from site_blocks where kind = 'SOCIAL' and slug = 'facebook');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'SOCIAL', 'instagram', 'Instagram', null, null, null, '', null, 'ig', 3, true
where not exists (select 1 from site_blocks where kind = 'SOCIAL' and slug = 'instagram');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'SOCIAL', 'tiktok', 'TikTok', null, null, null, 'https://www.tiktok.com/@dibidani', null, 'tt', 4, true
where not exists (select 1 from site_blocks where kind = 'SOCIAL' and slug = 'tiktok');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'SOCIAL', 'youtube', 'YouTube', null, null, null, '', null, 'yt', 5, false
where not exists (select 1 from site_blocks where kind = 'SOCIAL' and slug = 'youtube');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'SOCIAL', 'linkedin', 'LinkedIn', null, null, null, '', null, 'in', 6, false
where not exists (select 1 from site_blocks where kind = 'SOCIAL' and slug = 'linkedin');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'header', 'Toutes les catégories', null, null, null, '/category/all', null, null, 1, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'header');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'header', 'Pièces détachées', null, null, null, '/category/pieces-detachees', null, null, 2, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'header' and title = 'Pièces détachées');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'header', 'Quincaillerie', null, null, null, '/category/quincaillerie', null, null, 3, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'header' and title = 'Quincaillerie');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'header', 'Alimentation', null, null, null, '/category/alimentation', null, null, 4, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'header' and title = 'Alimentation');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'header', 'Promotions', null, null, null, '/category/all', null, 'promo', 9, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'header' and meta = 'promo');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'footer-dibidani', 'Accueil', null, null, null, '/', null, null, 1, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'footer-dibidani');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'footer-dibidani', 'Nos catégories', null, null, null, '/category/all', null, null, 2, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'footer-dibidani' and title = 'Nos catégories');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'footer-dibidani', 'À propos de Dibidani', null, null, null, '/p/a-propos', null, null, 3, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'footer-dibidani' and link_url = '/p/a-propos');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'footer-help', 'Suivre une commande', null, null, null, '/track', null, null, 1, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'footer-help');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'footer-help', 'Livraison', null, null, null, '/p/livraison', null, null, 2, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'footer-help' and link_url = '/p/livraison');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'footer-help', 'FAQ', null, null, null, '/p/faq', null, null, 3, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'footer-help' and link_url = '/p/faq');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'MENU', 'footer-help', 'WhatsApp', null, null, null, 'https://wa.me/22376123456', null, 'external', 4, true
where not exists (select 1 from site_blocks where kind = 'MENU' and slug = 'footer-help' and title = 'WhatsApp');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'trust', 'Livraison rapide', 'À Bamako et environs', null, null, null, null, '🚚', 1, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'trust' and title = 'Livraison rapide');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'trust', 'Paiement sécurisé', 'Mobile Money, carte, COD', null, null, null, null, '💳', 2, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'trust' and title = 'Paiement sécurisé');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'trust', 'Produits de qualité', 'Vendeurs vérifiés', null, null, null, null, '🛡️', 3, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'trust' and title = 'Produits de qualité');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'trust', 'Service client', 'Assistance WhatsApp', null, null, null, null, '💬', 4, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'trust' and title = 'Service client');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'categories', 'Nos catégories', null, null, null, '/category/all', 'Voir toutes', null, 10, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'categories');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'offers', 'Offres du jour 🔥', null, null, null, '/category/all', 'Voir toutes', null, 20, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'offers');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'ads-top', 'Bandeau publicitaire', null, null, null, null, null, null, 30, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'ads-top');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'ads-split', 'Encarts publicitaires', null, null, null, null, null, null, 40, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'ads-split');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'bestsellers', 'Meilleures ventes', null, null, null, '/category/all', 'Voir toutes', null, 50, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'bestsellers');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'testimonials', 'Témoignages', 'Ce que disent nos clients à Bamako', null, null, null, null, null, 60, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'testimonials');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'HOME', 'partners', 'Nos partenaires', 'Marques et services disponibles à Dibidani', null, null, null, null, null, 70, true
where not exists (select 1 from site_blocks where kind = 'HOME' and slug = 'partners');
