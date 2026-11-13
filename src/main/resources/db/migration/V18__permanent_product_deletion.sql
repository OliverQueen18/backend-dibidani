-- Preserve order snapshots and stock history after a product is deleted.
alter table order_items alter column product_id drop not null;
alter table order_items drop constraint order_items_product_id_fkey;
alter table order_items add constraint order_items_product_id_fkey
  foreign key (product_id) references products(id) on delete set null;

alter table stock_movements alter column product_id drop not null;
alter table stock_movements drop constraint stock_movements_product_id_fkey;
alter table stock_movements add constraint stock_movements_product_id_fkey
  foreign key (product_id) references products(id) on delete set null;

alter table social_posts drop constraint social_posts_product_id_fkey;
alter table social_posts add constraint social_posts_product_id_fkey
  foreign key (product_id) references products(id) on delete cascade;
