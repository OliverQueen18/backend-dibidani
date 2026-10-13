create table social_posts(
  id bigserial primary key,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  product_id bigint not null references products(id),
  channel varchar(20) not null,
  status varchar(20) not null,
  message varchar(2000) not null,
  product_url varchar(500),
  share_url varchar(1500),
  external_id varchar(255),
  error_message varchar(1000),
  mock boolean not null default false,
  published_by_id bigint references app_users(id) on delete set null,
  published_at timestamptz
);

alter table social_posts add constraint ck_social_posts_channel
  check (channel in ('FACEBOOK', 'TIKTOK', 'WHATSAPP'));
alter table social_posts add constraint ck_social_posts_status
  check (status in ('PENDING', 'PUBLISHED', 'SHARED', 'FAILED'));

create index ix_social_posts_product on social_posts(product_id, created_at desc);
create index ix_social_posts_channel on social_posts(channel, created_at desc);
