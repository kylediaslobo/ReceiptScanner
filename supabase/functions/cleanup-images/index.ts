import { createClient } from "npm:@supabase/supabase-js@2";

// Daily retention job, triggered by pg_cron (see migration 20261028000001).
// Keeps each user's 15 newest receipt images that are less than 14 days old and deletes the rest.

const BUCKET = "receipt-images";
const KEEP_COUNT = 15;
const KEEP_DAYS = 14;
const REMOVE_BATCH = 100;

Deno.serve(async (req) => {
  const expected = Deno.env.get("CLEANUP_SECRET");
  if (!expected || req.headers.get("x-cleanup-secret") !== expected) {
    return json({ error: "Forbidden" }, 403);
  }

  // Service role: needs to see and delete every user's images, and clear image_path on their receipts.
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  const { data, error } = await admin.rpc("receipt_images_to_purge", {
    keep_count: KEEP_COUNT,
    keep_days: KEEP_DAYS,
  });
  if (error) {
    console.error("receipt_images_to_purge failed", error);
    return json({ error: error.message }, 500);
  }

  const names = (data as { name: string }[]).map((row) => row.name);

  for (let i = 0; i < names.length; i += REMOVE_BATCH) {
    const batch = names.slice(i, i + REMOVE_BATCH);

    const { error: removeError } = await admin.storage.from(BUCKET).remove(batch);
    if (removeError) {
      console.error("Storage remove failed", removeError);
      return json({ error: removeError.message, deleted: i }, 500);
    }

    const { error: updateError } = await admin
      .from("receipts")
      .update({ image_path: null })
      .in("image_path", batch);
    if (updateError) console.error("Clearing image_path failed", updateError);
  }

  console.log(`Deleted ${names.length} receipt image(s)`);
  return json({ deleted: names.length });
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
