-- ---------- Stock : mouvements complets (avant / mouvement / après), types métier ----------
alter table stock_movements add column quantity_before integer;
update stock_movements set quantity_before = greatest(stock_after - quantity, 0);
alter table stock_movements alter column quantity_before set not null;
alter table stock_movements add column user_id bigint references app_users(id) on delete set null;
alter table stock_movements add column variant_name varchar(255);
alter table stock_movements rename column reason to comment;
alter table stock_movements alter column comment type varchar(1000);

update stock_movements set type = 'PURCHASE' where type = 'IN';
update stock_movements set type = 'ADJUSTMENT' where type = 'OUT';
update stock_movements set type = 'SALE' where type = 'ORDER';
alter table stock_movements add constraint ck_stock_movements_type
  check (type in ('PURCHASE', 'SALE', 'RETURN', 'CANCELLATION', 'ADJUSTMENT', 'LOSS', 'DAMAGE'));
alter table stock_movements add constraint ck_stock_movements_after check (stock_after >= 0 and quantity_before >= 0);
alter table stock_movements add constraint ck_stock_movements_delta check (quantity <> 0);

create index ix_stock_movements_product_date on stock_movements(product_id, created_at desc);
create index ix_stock_movements_type_date on stock_movements(type, created_at desc);
create index ix_stock_movements_reference on stock_movements(reference);

-- Aucun stock négatif, même en cas de bug applicatif.
update products set stock = 0 where stock < 0;
update product_variants set stock = 0 where stock < 0;
update products set low_stock_threshold = 0 where low_stock_threshold < 0;
alter table products add constraint ck_products_stock check (stock >= 0);
alter table products add constraint ck_products_low_stock check (low_stock_threshold >= 0);
alter table product_variants add constraint ck_product_variants_stock check (stock >= 0);

-- ---------- Sécurité : verrouillage, révocation des jetons, refresh tokens, audit ----------
alter table app_users add column failed_login_attempts integer not null default 0;
alter table app_users add column locked_until timestamptz;
alter table app_users add column token_version integer not null default 0;
alter table app_users add column password_changed_at timestamptz;

alter table app_users add constraint ck_app_users_role
  check (role in ('CUSTOMER', 'ADMIN', 'MANAGER', 'CATALOG_MANAGER', 'ORDER_MANAGER', 'STOCK_MANAGER', 'COURIER'));

create table refresh_tokens(
  id bigserial primary key,
  created_at timestamptz not null, updated_at timestamptz not null,
  user_id bigint not null references app_users(id) on delete cascade,
  token_hash varchar(64) not null unique,
  family_id varchar(64) not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  revoked_reason varchar(100),
  replaced_by_id bigint references refresh_tokens(id) on delete set null,
  created_ip varchar(64),
  user_agent varchar(300));
create index ix_refresh_tokens_user on refresh_tokens(user_id);
create index ix_refresh_tokens_family on refresh_tokens(family_id);
create index ix_refresh_tokens_expires on refresh_tokens(expires_at);

create table audit_logs(
  id bigserial primary key,
  created_at timestamptz not null, updated_at timestamptz not null,
  actor_id bigint,
  actor_email varchar(255),
  action varchar(80) not null,
  entity_type varchar(60),
  entity_id varchar(80),
  success boolean not null default true,
  ip varchar(64),
  user_agent varchar(300),
  details varchar(2000));
create index ix_audit_logs_created on audit_logs(created_at desc);
create index ix_audit_logs_actor on audit_logs(actor_email, created_at desc);
create index ix_audit_logs_action on audit_logs(action, created_at desc);
create index ix_audit_logs_entity on audit_logs(entity_type, entity_id);
