create table password_reset_otps (
  id bigserial primary key,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  user_id bigint not null references app_users(id) on delete cascade,
  code_hash varchar(255) not null,
  expires_at timestamptz not null,
  attempts integer not null default 0,
  consumed_at timestamptz,
  email_sent boolean not null default false,
  sms_sent boolean not null default false,
  created_ip varchar(64)
);

create index ix_password_reset_otps_user on password_reset_otps(user_id, created_at desc);
create index ix_password_reset_otps_expires on password_reset_otps(expires_at);
