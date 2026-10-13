alter table app_users drop constraint if exists ck_app_users_role;
alter table app_users add constraint ck_app_users_role
  check (role in ('CUSTOMER', 'SELLER', 'ADMIN', 'MANAGER', 'CATALOG_MANAGER', 'ORDER_MANAGER', 'STOCK_MANAGER', 'COURIER'));
