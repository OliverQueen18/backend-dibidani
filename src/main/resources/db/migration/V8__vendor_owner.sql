alter table vendors
  add column owner_id bigint unique references app_users(id);
create index idx_vendor_owner on vendors(owner_id);
