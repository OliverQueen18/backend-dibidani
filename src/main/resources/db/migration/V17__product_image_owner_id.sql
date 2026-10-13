-- Align Hibernate mapping (owner_id) with V16's owner_user_id.
do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public'
      and table_name = 'product_image_assets'
      and column_name = 'owner_user_id'
  ) and not exists (
    select 1 from information_schema.columns
    where table_schema = 'public'
      and table_name = 'product_image_assets'
      and column_name = 'owner_id'
  ) then
    alter table product_image_assets rename column owner_user_id to owner_id;
  end if;
end $$;
