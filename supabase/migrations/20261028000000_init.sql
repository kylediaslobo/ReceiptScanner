create table public.receipts (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
    title            text not null check (char_length(title) between 1 and 200),
    merchant_name    text,
    merchant_address text,
    purchase_date    date,
    purchase_time    time,
    currency         text check (currency ~ '^[A-Z]{3}$'),
    subtotal         numeric(12,2),
    tax              numeric(12,2),
    tip              numeric(12,2),
    total            numeric(12,2),
    payment_method   text,
    extra            jsonb not null default '{}'::jsonb,
    image_path       text,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

create index receipts_user_created_idx on public.receipts (user_id, created_at desc);

create table public.receipt_items (
    id          uuid primary key default gen_random_uuid(),
    receipt_id  uuid not null references public.receipts (id) on delete cascade,
    user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
    line_no     int not null default 0,
    description text not null,
    quantity    numeric(10,3) not null default 1,
    unit_price  numeric(12,2),
    amount      numeric(12,2)
);

create index receipt_items_receipt_idx on public.receipt_items (receipt_id, line_no);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

create trigger receipts_set_updated_at
    before update on public.receipts
    for each row execute function public.set_updated_at();

alter table public.receipts      enable row level security;
alter table public.receipt_items enable row level security;

create policy "Own receipts" on public.receipts
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

create policy "Own receipt items" on public.receipt_items
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (
        user_id = (select auth.uid())
        and exists (
            select 1 from public.receipts r
            where r.id = receipt_id and r.user_id = (select auth.uid())
        )
    );

create or replace function public.save_receipt(p_receipt jsonb, p_items jsonb default '[]'::jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
    r    public.receipts := jsonb_populate_record(null::public.receipts, p_receipt);
    v_id uuid := r.id;
begin
    if v_id is null then
        insert into public.receipts
            (title, merchant_name, merchant_address, purchase_date, purchase_time, currency, subtotal, tax, tip, total, payment_method, extra, image_path)
        values
            (r.title, r.merchant_name, r.merchant_address, r.purchase_date, r.purchase_time, r.currency, r.subtotal, r.tax, r.tip, r.total, r.payment_method, coalesce(r.extra, '{}'::jsonb), r.image_path)
        returning id into v_id;
    else
        update public.receipts set
            title            = r.title,
            merchant_name    = r.merchant_name,
            merchant_address = r.merchant_address,
            purchase_date    = r.purchase_date,
            purchase_time    = r.purchase_time,
            currency         = r.currency,
            subtotal         = r.subtotal,
            tax              = r.tax,
            tip              = r.tip,
            total            = r.total,
            payment_method   = r.payment_method,
            extra            = coalesce(r.extra, '{}'::jsonb),
            image_path       = r.image_path
        where id = v_id;

        if not found then
            raise exception 'Receipt % not found', v_id using errcode = 'P0002';
        end if;

        delete from public.receipt_items where receipt_id = v_id;
    end if;

    insert into public.receipt_items (receipt_id, line_no, description, quantity, unit_price, amount)
    select v_id, t.ord::int, l.description, coalesce(l.quantity, 1), l.unit_price, l.amount
    from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) with ordinality as t(item, ord), jsonb_populate_record(null::public.receipt_items, t.item) as l;

    return v_id;
end;
$$;

revoke execute on function public.save_receipt(jsonb, jsonb) from public, anon;
grant  execute on function public.save_receipt(jsonb, jsonb) to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('receipt-images', 'receipt-images', false, 5242880, array['image/jpeg']) on conflict (id) do nothing;

create policy "Upload own receipt images" on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'receipt-images'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

create policy "Read own receipt images" on storage.objects
    for select to authenticated
    using (
        bucket_id = 'receipt-images'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

create policy "Delete own receipt images" on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'receipt-images'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );
