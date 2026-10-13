-- Extensions de recherche (extensions "trusted" depuis PostgreSQL 13 : le propriétaire de la base peut les créer)
create extension if not exists pg_trgm;
create extension if not exists unaccent;

create or replace function f_unaccent(text) returns text
  language sql immutable parallel safe strict
  as $$ select public.unaccent('public.unaccent'::regdictionary, $1) $$;

create or replace function f_slugify(text) returns text
  language sql immutable parallel safe strict
  as $$ select trim(both '-' from regexp_replace(lower(f_unaccent($1)), '[^a-z0-9]+', '-', 'g')) $$;

-- Catégories hiérarchiques
alter table categories
  add column description text,
  add column parent_id bigint references categories(id),
  add column featured boolean not null default false;
update categories set display_order = 0 where display_order is null;
alter table categories alter column display_order set default 0;
alter table categories alter column display_order set not null;
alter table categories drop constraint if exists categories_name_key;
create index idx_category_parent on categories(parent_id);
create index idx_category_active_order on categories(active, display_order);

-- Vendeurs (entités administrées par Dibidani, sans compte)
alter table vendors
  add column slug varchar(255),
  add column city varchar(120);
update vendors set slug = f_slugify(name);
update vendors v set slug = v.slug || '-' || v.id
  where exists (select 1 from vendors o where o.slug = v.slug and o.id < v.id);
update vendors set city = 'Bamako' where city is null;
alter table vendors alter column slug set not null;
alter table vendors add constraint vendors_slug_key unique (slug);
alter table vendors alter column description type text;

-- Produits
alter table products
  add column subcategory_id bigint references categories(id),
  add column cost_price numeric(14,2),
  add column weight_kg numeric(10,3),
  add column length_cm numeric(10,2),
  add column width_cm numeric(10,2),
  add column height_cm numeric(10,2),
  add column new_arrival boolean not null default false;
create index idx_product_subcategory on products(subcategory_id);
create index idx_product_brand on products(brand_id);
create index idx_product_vendor on products(vendor_id);
create index idx_product_active_created on products(active, created_at desc);
create index idx_product_promo on products(created_at desc) where promo_price is not null and active;

alter table product_images add column position integer not null default 0;
update product_images pi set position = sub.rn
  from (select ctid, row_number() over (partition by product_id) - 1 as rn from product_images) sub
  where pi.ctid = sub.ctid;

create table product_variants(
  id bigserial primary key,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  product_id bigint not null references products(id) on delete cascade,
  name varchar(255) not null,
  sku varchar(255) unique,
  price numeric(14,2),
  stock integer not null default 0,
  active boolean not null default true,
  position integer not null default 0
);
create index idx_variant_product on product_variants(product_id);

create table product_specifications(
  product_id bigint not null references products(id) on delete cascade,
  position integer not null,
  label varchar(255) not null,
  spec_value varchar(1000) not null,
  primary key (product_id, position)
);

-- Lignes de commande : instantané du produit et variante éventuelle
alter table order_items
  add column product_name varchar(255),
  add column variant_id bigint references product_variants(id) on delete set null,
  add column variant_name varchar(255);
update order_items oi set product_name = p.name from products p where p.id = oi.product_id;

-- Publicités
alter table advertisements rename column subtitle to description;
alter table advertisements alter column description type text;
alter table advertisements rename column placement to position;
alter table advertisements rename column starts_at to start_date;
alter table advertisements rename column ends_at to end_date;
alter table advertisements
  add column mobile_image_url varchar(500),
  add column type varchar(40) not null default 'BANNER',
  add column display_order integer not null default 0,
  add column cta_label varchar(80);
update advertisements set position = 'HOME_TOP' where position = 'HOME_BANNER';
update advertisements set type = 'SLIDER' where position = 'HOME_HERO';
update advertisements set position = 'HOME_TOP'
  where position not in ('HOME_HERO','HOME_TOP','HOME_MIDDLE','HOME_BOTTOM','CATEGORY_TOP','CATEGORY_MIDDLE','PRODUCT_SIDEBAR','CART','CHECKOUT');
update advertisements set start_date = coalesce(start_date, created_at), end_date = coalesce(end_date, now() + interval '5 years');
alter table advertisements alter column start_date set not null;
alter table advertisements alter column end_date set not null;
create index idx_ad_position on advertisements(position, active, start_date, end_date, display_order);

-- Newsletter
create table newsletter_subscribers(
  id bigserial primary key,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  email varchar(255) not null unique,
  active boolean not null default true
);

-- Index trigrammes pour la recherche (expressions identiques à celles des requêtes)
drop index if exists idx_product_name;
create index idx_product_name_trgm on products using gin (lower(f_unaccent(name)) gin_trgm_ops);
create index idx_product_sku_trgm on products using gin (lower(sku) gin_trgm_ops);
create index idx_product_short_desc_trgm on products using gin (lower(f_unaccent(coalesce(short_description, ''))) gin_trgm_ops);
create index idx_product_desc_trgm on products using gin (lower(f_unaccent(coalesce(description, ''))) gin_trgm_ops);
create index idx_brand_name_trgm on brands using gin (lower(f_unaccent(name)) gin_trgm_ops);
create index idx_vendor_name_trgm on vendors using gin (lower(f_unaccent(name)) gin_trgm_ops);
create index idx_category_name_trgm on categories using gin (lower(f_unaccent(name)) gin_trgm_ops);
