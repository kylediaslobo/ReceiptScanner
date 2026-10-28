-- Image retention: keep each user's 15 newest receipt images for up to 14 days, delete the rest daily.
-- Also exposes the recent images to the app so a lost scan can be recovered.

-- Which images the cleanup job should delete. Only the service role (the cleanup-images function) may call it.
create or replace function public.receipt_images_to_purge(keep_count int default 15, keep_days int default 14)
returns table (name text)
language sql
security definer
set search_path = ''
as $$
  select o.name
  from (
    select
      name,
      created_at,
      row_number() over (
        partition by (storage.foldername(name))[1]
        order by created_at desc
      ) as rn
    from storage.objects
    where bucket_id = 'receipt-images'
  ) o
  where o.rn > keep_count
     or o.created_at < now() - make_interval(days => keep_days);
$$;

revoke execute on function public.receipt_images_to_purge(int, int) from public, anon, authenticated;
grant  execute on function public.receipt_images_to_purge(int, int) to service_role;

-- The signed-in user's recent images, newest first, for the "Recent photos" recovery screen.
-- Runs as the caller, so the storage.objects policies limit it to their own folder.
create or replace function public.list_recent_receipt_images()
returns table (name text, created_at timestamptz)
language sql
security invoker
set search_path = ''
as $$
  select o.name, o.created_at
  from storage.objects o
  where o.bucket_id = 'receipt-images'
    and (storage.foldername(o.name))[1] = (select auth.uid())::text
  order by o.created_at desc
  limit 15;
$$;

revoke execute on function public.list_recent_receipt_images() from public, anon;
grant  execute on function public.list_recent_receipt_images() to authenticated;

-- Daily schedule. pg_cron calls the cleanup-images Edge Function through pg_net.
-- The project URL and shared secret live in Vault (see README: "Image cleanup schedule").
create extension if not exists pg_cron;
create extension if not exists pg_net with schema extensions;

select cron.schedule(
  'cleanup-receipt-images',
  '17 3 * * *',
  $$
  select net.http_post(
    url     := (select decrypted_secret from vault.decrypted_secrets where name = 'project_url') || '/functions/v1/cleanup-images',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cleanup-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'cleanup_secret')
    ),
    body    := '{}'::jsonb
  );
  $$
);
