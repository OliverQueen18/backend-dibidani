alter table product_image_assets add column model varchar(255);
alter table product_image_assets add column output_format varchar(255);
alter table product_image_assets add column output_width integer;
alter table product_image_assets add column output_height integer;
alter table product_image_assets add column output_bytes bigint;
-- Old interrupted synchronous jobs can safely be retried from their intact originals.
update product_image_jobs set status='FAILED', message='Traitement interrompu. Vous pouvez réessayer.', finished_at=now()
where status in ('PENDING', 'PROCESSING');
create unique index idx_studio_one_active_job on product_image_jobs(asset_id)
where status in ('PENDING', 'PROCESSING');
