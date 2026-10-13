-- V3 : véhicules (Trouver ma pièce), livraison, promotions, panier, checkout, workflow de commande, paiements, stocks, back-office.

-- ---------- Utilisateurs ----------
alter table app_users add column last_login_at timestamptz;
create index idx_users_role on app_users(role);
create index idx_users_created on app_users(created_at);

-- ---------- Unités ----------
create table units(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  name varchar(100) not null unique, symbol varchar(30), active boolean not null default true, display_order integer not null default 0);
insert into units(created_at, updated_at, name, symbol, display_order) values
  (now(), now(), 'unité', 'u', 0), (now(), now(), 'pièce', 'pce', 1), (now(), now(), 'kg', 'kg', 2), (now(), now(), 'litre', 'L', 3),
  (now(), now(), 'sac', 'sac', 4), (now(), now(), 'carton', 'ctn', 5), (now(), now(), 'pack', 'pack', 6), (now(), now(), 'paire', 'paire', 7),
  (now(), now(), 'mètre', 'm', 8), (now(), now(), 'kit', 'kit', 9);
insert into units(created_at, updated_at, name, display_order)
  select now(), now(), u, 100 from (select distinct unit as u from products) d where not exists (select 1 from units where name = d.u);

-- ---------- Paramètres modifiables depuis le back-office ----------
create table shop_settings(setting_key varchar(100) primary key, setting_value text, updated_at timestamptz not null);

-- ---------- Stocks ----------
create table stock_movements(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  product_id bigint not null references products(id), variant_id bigint references product_variants(id) on delete set null,
  type varchar(30) not null, quantity integer not null, stock_after integer not null,
  reason varchar(500), reference varchar(100), created_by varchar(255));
create index idx_stock_mov_product on stock_movements(product_id, created_at desc);
create index idx_products_stock on products(stock);

-- ---------- Références pièces détachées ----------
alter table products add column oem_reference varchar(255);
alter table products add column manufacturer_reference varchar(255);
create index idx_products_oem_trgm on products using gin (lower(f_unaccent(coalesce(oem_reference, ''))) gin_trgm_ops);
create index idx_products_mfr_trgm on products using gin (lower(f_unaccent(coalesce(manufacturer_reference, ''))) gin_trgm_ops);

-- ---------- Véhicules ----------
create table vehicle_brands(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  name varchar(150) not null, slug varchar(180) not null unique, logo_url varchar(500),
  active boolean not null default true, display_order integer not null default 0);
create table vehicle_models(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  brand_id bigint not null references vehicle_brands(id), name varchar(150) not null, slug varchar(180) not null,
  vehicle_type varchar(30) not null, active boolean not null default true, unique(brand_id, slug));
create index idx_vehicle_models_type on vehicle_models(vehicle_type, brand_id);
create table vehicle_engines(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  model_id bigint not null references vehicle_models(id), name varchar(150) not null, fuel_type varchar(30),
  displacement_cc integer, power_hp integer, year_from integer not null, year_to integer,
  active boolean not null default true, check (year_to is null or year_to >= year_from));
create index idx_vehicle_engines_model on vehicle_engines(model_id);
create table vehicle_compatibilities(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  product_id bigint not null references products(id) on delete cascade, engine_id bigint not null references vehicle_engines(id),
  year_from integer, year_to integer, notes varchar(500), unique(product_id, engine_id),
  check (year_to is null or year_from is null or year_to >= year_from));
create index idx_vehicle_compat_engine on vehicle_compatibilities(engine_id);

-- ---------- Zones de livraison ----------
create table delivery_zones(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  city varchar(120) not null, commune varchar(120), district varchar(150) not null, name varchar(200) not null,
  standard_price numeric(14,2) not null, express_price numeric(14,2),
  estimated_standard_time varchar(100), estimated_express_time varchar(100),
  active boolean not null default true, display_order integer not null default 0,
  unique(city, commune, district));
create index idx_delivery_zones_active on delivery_zones(active, city, commune);

-- ---------- Codes promo ----------
create table promo_codes(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  code varchar(50) not null unique, description varchar(500), type varchar(30) not null, value numeric(14,2) not null,
  min_subtotal numeric(14,2), max_discount numeric(14,2), start_date timestamptz, end_date timestamptz,
  usage_limit integer, used_count integer not null default 0, active boolean not null default true);

-- ---------- Adresses, favoris ----------
create table customer_addresses(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  user_id bigint not null references app_users(id) on delete cascade, label varchar(100), full_name varchar(255) not null,
  phone varchar(50) not null, address_line varchar(500) not null, delivery_zone_id bigint references delivery_zones(id),
  landmark varchar(500), is_default boolean not null default false);
create index idx_addresses_user on customer_addresses(user_id);
create table favorites(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  user_id bigint not null references app_users(id) on delete cascade, product_id bigint not null references products(id) on delete cascade,
  unique(user_id, product_id));

-- ---------- Panier ----------
create table carts(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  user_id bigint not null unique references app_users(id) on delete cascade, promo_code varchar(50));
create table cart_items(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  cart_id bigint not null references carts(id) on delete cascade, product_id bigint not null references products(id) on delete cascade,
  variant_id bigint references product_variants(id) on delete cascade, quantity integer not null check (quantity > 0));
create unique index uq_cart_items_line on cart_items(cart_id, product_id, coalesce(variant_id, 0));

-- ---------- Checkout sauvegardé ----------
create table checkout_sessions(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  user_id bigint not null unique references app_users(id) on delete cascade,
  full_name varchar(255), phone varchar(50), email varchar(255),
  address_line varchar(500), delivery_zone_id bigint references delivery_zones(id), landmark varchar(500), address_id bigint,
  delivery_method varchar(30), payment_method varchar(40), payment_provider varchar(40), payment_phone varchar(50),
  step integer not null default 1);

-- ---------- Commandes ----------
update customer_orders set status = 'PENDING_PAYMENT' where status = 'AWAITING_PAYMENT';
update customer_orders set payment_method = case
  when payment_method is null then null
  when upper(payment_method) like '%ORANGE%' or upper(payment_method) like '%MOOV%' or upper(payment_method) like '%MOBILE%' then 'MOBILE_MONEY'
  when upper(payment_method) like '%CARD%' or upper(payment_method) like '%CARTE%' then 'BANK_CARD'
  when upper(payment_method) like '%TRANSFER%' or upper(payment_method) like '%VIREMENT%' then 'BANK_TRANSFER'
  when upper(payment_method) like '%STORE%' or upper(payment_method) like '%MAGASIN%' then 'CASH_AT_STORE'
  else 'CASH_ON_DELIVERY' end;

alter table customer_orders add column customer_name varchar(255);
alter table customer_orders add column customer_email varchar(255);
alter table customer_orders add column delivery_method varchar(30) not null default 'STANDARD';
alter table customer_orders add column delivery_zone_id bigint references delivery_zones(id);
alter table customer_orders add column delivery_zone_name varchar(200);
alter table customer_orders add column delivery_commune varchar(120);
alter table customer_orders add column delivery_city varchar(120);
alter table customer_orders add column delivery_landmark varchar(500);
alter table customer_orders add column estimated_delivery varchar(100);
alter table customer_orders add column discount_total numeric(14,2) not null default 0;
alter table customer_orders add column promo_code varchar(50);
alter table customer_orders add column payment_provider varchar(40);
alter table customer_orders add column tracking_token varchar(64);
alter table customer_orders add column stock_released boolean not null default false;
alter table customer_orders add column admin_note varchar(1000);

update customer_orders o set customer_name = u.full_name, customer_email = u.email, delivery_city = 'Bamako'
  from app_users u where u.id = o.customer_id;
update customer_orders set tracking_token = replace(gen_random_uuid()::text, '-', '') || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8);
alter table customer_orders alter column tracking_token set not null;
alter table customer_orders alter column delivery_address drop not null;
create unique index uq_orders_tracking_token on customer_orders(tracking_token);
create index idx_orders_status on customer_orders(status);
create index idx_orders_created on customer_orders(created_at desc);

alter table order_items add column product_sku varchar(255);
alter table order_items add column vendor_id bigint;
alter table order_items add column vendor_name varchar(255);
alter table order_items add column image_url varchar(500);
update order_items i set product_name = coalesce(i.product_name, p.name), product_sku = p.sku, vendor_id = p.vendor_id,
  vendor_name = v.name, image_url = p.main_image_url
  from products p left join vendors v on v.id = p.vendor_id where p.id = i.product_id;
update order_items set product_name = 'Produit' where product_name is null;
update order_items set product_sku = '-' where product_sku is null;
alter table order_items alter column product_name set not null;
alter table order_items alter column product_sku set not null;
create index idx_order_items_product on order_items(product_id);

create table order_status_history(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  order_id bigint not null references customer_orders(id) on delete cascade,
  from_status varchar(40), to_status varchar(40) not null,
  changed_by_id bigint references app_users(id) on delete set null, changed_by_name varchar(255) not null,
  comment varchar(1000));
create index idx_order_history_order on order_status_history(order_id, created_at);
insert into order_status_history(created_at, updated_at, order_id, from_status, to_status, changed_by_name, comment)
  select created_at, created_at, id, null, status, 'Système', 'Statut repris lors de la migration' from customer_orders;

-- ---------- Livraisons (préparation application Livreur) ----------
create table deliveries(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  order_id bigint not null unique references customer_orders(id) on delete cascade,
  method varchar(30) not null, status varchar(30) not null,
  courier_id bigint references app_users(id) on delete set null, courier_name varchar(255), courier_phone varchar(50),
  assigned_at timestamptz, picked_up_at timestamptz, delivered_at timestamptz, notes varchar(1000));
create index idx_deliveries_status on deliveries(status);
create index idx_deliveries_courier on deliveries(courier_id);

-- ---------- Paiements ----------
alter table payments add column provider varchar(40);
alter table payments add column currency varchar(3) not null default 'XOF';
alter table payments add column payer_phone varchar(50);
alter table payments add column paid_at timestamptz;
alter table payments add column failure_reason varchar(500);
alter table payments add column checkout_url varchar(1000);
alter table payments add column provider_token varchar(255);
update payments set provider = case
  when upper(method) like '%ORANGE%' then 'ORANGE_MONEY'
  when upper(method) like '%MOOV%' then 'MOOV_MONEY'
  else 'MOCK' end;
update payments set method = case
  when upper(method) like '%ORANGE%' or upper(method) like '%MOOV%' or upper(method) like '%MOBILE%' then 'MOBILE_MONEY'
  when upper(method) like '%CARD%' or upper(method) like '%CARTE%' then 'BANK_CARD'
  when upper(method) like '%TRANSFER%' then 'BANK_TRANSFER'
  when upper(method) like '%STORE%' then 'CASH_AT_STORE'
  else 'CASH_ON_DELIVERY' end;
update payments set status = 'SUCCESS', paid_at = updated_at where status = 'PAID';
alter table customer_orders add column payment_status varchar(30);
update customer_orders o set payment_provider = p.provider, payment_status = p.status from payments p where p.order_id = o.id;
update customer_orders set payment_status = 'PENDING' where payment_status is null;
alter table customer_orders alter column payment_status set not null;
create index idx_orders_payment_status on customer_orders(payment_status);
create unique index uq_payments_provider_ref on payments(provider, provider_reference) where provider_reference is not null;
create index idx_payments_status on payments(status);

create table payment_transactions(
  id bigserial primary key, created_at timestamptz not null, updated_at timestamptz not null,
  payment_id bigint not null references payments(id) on delete cascade,
  type varchar(30) not null, status varchar(30) not null, amount numeric(14,2) not null,
  provider varchar(40) not null, provider_reference varchar(255), message varchar(1000), raw_payload text);
create index idx_payment_tx_payment on payment_transactions(payment_id, created_at);
create index idx_payment_tx_ref on payment_transactions(provider, provider_reference);
