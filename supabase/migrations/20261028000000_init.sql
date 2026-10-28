-- One row per receipt, shaped like a line on the Entertainment & Expense Claims form:
-- Date | Nature of Expense | Company | Names | Amount (Rand).
create table public.receipts (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid not null default auth.uid() references auth.users (id) on delete cascade,
    expense_date      date not null,
    nature_of_expense text not null check (char_length(nature_of_expense) between 1 and 200),
    company           text not null check (char_length(company) between 1 and 200),
    names             text not null check (char_length(names) between 1 and 200),
    amount            numeric(12,2) not null,
    image_path        text,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create index receipts_user_date_idx on public.receipts (user_id, expense_date desc, created_at desc);

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

alter table public.receipts enable row level security;

create policy "Own receipts" on public.receipts
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

-- Inserts a new receipt (no id) or updates an existing one, and returns its id.
create or replace function public.save_receipt(p_receipt jsonb)
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
        insert into public.receipts (expense_date, nature_of_expense, company, names, amount, image_path)
        values (r.expense_date, r.nature_of_expense, r.company, r.names, r.amount, r.image_path)
        returning id into v_id;
    else
        update public.receipts set
            expense_date      = r.expense_date,
            nature_of_expense = r.nature_of_expense,
            company           = r.company,
            names             = r.names,
            amount            = r.amount,
            image_path        = r.image_path
        where id = v_id;

        if not found then
            raise exception 'Receipt % not found', v_id using errcode = 'P0002';
        end if;
    end if;

    return v_id;
end;
$$;

revoke execute on function public.save_receipt(jsonb) from public, anon;
grant  execute on function public.save_receipt(jsonb) to authenticated;

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
