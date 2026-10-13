alter table app_users
  add column oauth_provider varchar(20),
  add column oauth_subject varchar(255);

create unique index uk_app_users_oauth on app_users (oauth_provider, oauth_subject)
  where oauth_provider is not null and oauth_subject is not null;
