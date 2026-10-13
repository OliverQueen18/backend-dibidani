update site_blocks
set subtitle = 'Commandes, paiements, livraison et suivi',
    body = 'Retrouvez ici les réponses aux questions les plus fréquentes sur Dibidani. Cliquez une question pour afficher la réponse. L’équipe reste joignable sur WhatsApp si besoin.',
    updated_at = now()
where kind = 'PAGE' and slug = 'faq';

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Qu’est-ce que Dibidani ?', null,
  'Dibidani est le marché de Bamako en ligne : pièces détachées, quincaillerie, alimentation, électronique, électroménager et énergie solaire. Nous mettons en relation clients et vendeurs, puis nous livrons à domicile, au bureau ou partout à Bamako.',
  null, '/p/faq', null, null, 1, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Qu’est-ce que Dibidani ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Comment passer une commande ?', null,
  'Cherchez un produit (barre de recherche, catégories ou assistant Dibidani), ajoutez-le au panier, puis validez l’adresse et le paiement. Après la commande, nous vérifions la disponibilité auprès des vendeurs avant de vous confirmer.',
  null, '/p/faq', null, null, 2, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Comment passer une commande ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Faut-il créer un compte ?', null,
  'Oui, un compte client permet de commander et de suivre vos livraisons. Vous pouvez aussi créer un compte vendeur pour proposer vos produits sur Dibidani. L’inscription se fait depuis la page de connexion.',
  null, '/login', null, null, 3, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Faut-il créer un compte ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Les produits sont-ils toujours en stock ?', null,
  'Dibidani ne gère pas d’entrepôt. La disponibilité est confirmée auprès du vendeur à chaque commande. Un article affiché « Dispo. à confirmer » sera vérifié avant que nous vous confirmions la commande.',
  null, '/p/faq', null, null, 4, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Les produits sont-ils toujours en stock ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Quels moyens de paiement acceptez-vous ?', null,
  'Vous pouvez payer par Orange Money, Moov Money, Wave, carte bancaire ou à la livraison (espèces). Le paiement mobile se valide sur votre téléphone.',
  null, '/p/faq', null, null, 5, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Quels moyens de paiement acceptez-vous ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Combien coûte la livraison ?', null,
  'La livraison standard à Bamako est à partir de 2 500 FCFA (24 h à 48 h). Un tarif express est proposé selon la disponibilité. Le montant exact dépend du quartier et s’affiche au moment de la commande. Le retrait au point relais est aussi possible.',
  null, '/p/livraison', null, null, 6, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Combien coûte la livraison ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Dans quelles zones livrez-vous ?', null,
  'Nous livrons à Bamako et dans les communes environnantes. Indiquez votre quartier à la commande : le tarif de livraison s’ajuste selon la zone.',
  null, '/p/livraison', null, null, 7, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Dans quelles zones livrez-vous ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Comment suivre ma commande ?', null,
  'Après validation, vous recevez un numéro de commande par SMS ou e-mail. Saisissez-le sur la page « Suivre une commande » (lien dans le pied de page) pour voir l’état : réception, confirmation vendeur, préparation, puis livraison.',
  null, '/track', null, null, 8, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Comment suivre ma commande ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Puis-je modifier ou annuler une commande ?', null,
  'Tant que la commande n’est pas confirmée au client (vérification auprès des vendeurs), contactez-nous sur WhatsApp avec votre numéro de commande. Après confirmation ou départ en livraison, une modification n’est plus toujours possible.',
  null, '/p/faq', null, null, 9, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Puis-je modifier ou annuler une commande ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Comment devenir vendeur sur Dibidani ?', null,
  'Créez un compte vendeur depuis la page d’inscription, renseignez votre boutique, puis proposez vos produits. Dibidani se charge de la mise en relation avec les clients et de la livraison.',
  null, '/login', null, null, 10, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Comment devenir vendeur sur Dibidani ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Que faire en cas de produit manquant ou endommagé ?', null,
  'Prévenez-nous dès la réception, avec votre numéro de commande et une photo si possible. Nous contactons le vendeur pour un remplacement, un complément ou un remboursement selon le cas.',
  null, '/p/faq', null, null, 11, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Que faire en cas de produit manquant ou endommagé ?');

insert into site_blocks (kind, slug, title, subtitle, body, image_url, link_url, cta_label, meta, display_order, active)
select 'FAQ', 'faq', 'Comment vous contacter ?', null,
  'Écrivez-nous sur WhatsApp (icône dans le bandeau et le pied de page), utilisez l’assistant Dibidani sur le site, ou appelez le numéro affiché en bas de page. L’équipe répond pendant les horaires d’ouverture.',
  null, '/p/faq', null, null, 12, true
where not exists (select 1 from site_blocks where kind = 'FAQ' and title = 'Comment vous contacter ?');
