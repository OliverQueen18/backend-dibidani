create table product_image_assets (
  id bigserial primary key,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  product_id bigint references products(id) on delete set null,
  vendor_id bigint references vendors(id) on delete set null,
  owner_user_id bigint not null references app_users(id),
  original_path varchar(500) not null,
  original_mime varchar(80),
  original_width integer,
  original_height integer,
  original_bytes integer,
  sha256 varchar(64),
  master_url varchar(500),
  catalog_url varchar(500),
  thumb_url varchar(500),
  jpeg_url varchar(500),
  processing_version varchar(32),
  engine varchar(40),
  params_json text,
  status varchar(32) not null,
  confirmed boolean not null default false
);
create index idx_product_image_assets_product on product_image_assets(product_id);
create index idx_product_image_assets_owner on product_image_assets(owner_user_id);
create index idx_product_image_assets_vendor on product_image_assets(vendor_id);

create table product_image_jobs (
  id bigserial primary key,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  asset_id bigint not null references product_image_assets(id) on delete cascade,
  status varchar(32) not null,
  progress integer not null default 0,
  message varchar(500),
  options_json text,
  started_at timestamptz,
  finished_at timestamptz,
  created_by varchar(255)
);
create index idx_product_image_jobs_asset on product_image_jobs(asset_id);
create index idx_product_image_jobs_status on product_image_jobs(status);

create table photo_studio_settings (
  id bigint primary key,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  engine varchar(40) not null default 'local',
  watermark_enabled boolean not null default true,
  watermark_position varchar(40) not null default 'BOTTOM_RIGHT',
  watermark_size double precision not null default 0.06,
  watermark_opacity double precision not null default 0.65,
  watermark_margin double precision not null default 0.025,
  watermark_variant varchar(40) not null default 'favicon',
  fill_ratio double precision not null default 0.82,
  shadow_enabled boolean not null default false
);
insert into photo_studio_settings (
  id, created_at, updated_at, engine, watermark_enabled, watermark_position,
  watermark_size, watermark_opacity, watermark_margin, watermark_variant, fill_ratio, shadow_enabled
) values (
  1, now(), now(), 'local', true, 'BOTTOM_RIGHT', 0.06, 0.65, 0.025, 'favicon', 0.82, false
);
