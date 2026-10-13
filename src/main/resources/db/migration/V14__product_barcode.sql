alter table products add column barcode varchar(64);

create unique index uk_products_barcode on products (lower(barcode))
  where barcode is not null and barcode <> '';

create index idx_product_barcode_trgm on products using gin (lower(barcode) gin_trgm_ops);
