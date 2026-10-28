import Anthropic from "npm:@anthropic-ai/sdk@^1.3.0";
import { createClient } from "npm:@supabase/supabase-js@2";
import { encodeBase64 } from "jsr:@std/encoding@1/base64";

const BUCKET = "receipt-images";
const MODEL = "claude-haiku-5-5";

// Reads ANTHROPIC_API_KEY from the function's secrets.
const anthropic = new Anthropic();

const nullable = (schema: Record<string, unknown>) => ({ anyOf: [schema, { type: "null" }] });

// Field names match the receipts / receipt_items columns, so the app can pass them straight to save_receipt.
const RECEIPT_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: [
    "merchant_name", "merchant_address", "purchase_date", "purchase_time", "currency",
    "subtotal", "tax", "tip", "total", "payment_method", "items", "extra",
  ],
  properties: {
    merchant_name: nullable({ type: "string" }),
    merchant_address: nullable({ type: "string" }),
    purchase_date: nullable({ type: "string", format: "date" }),
    purchase_time: nullable({ type: "string", description: "24-hour time, HH:MM" }),
    currency: nullable({ type: "string", description: "ISO 4217 code, e.g. EUR, USD, GBP" }),
    subtotal: nullable({ type: "number" }),
    tax: nullable({ type: "number" }),
    tip: nullable({ type: "number" }),
    total: nullable({ type: "number" }),
    payment_method: nullable({ type: "string" }),
    items: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["description", "quantity", "unit_price", "amount"],
        properties: {
          description: { type: "string" },
          quantity: nullable({ type: "number" }),
          unit_price: nullable({ type: "number" }),
          amount: nullable({ type: "number" }),
        },
      },
    },
    extra: {
      type: "array",
      description: "Other useful printed details, e.g. table number, server, receipt number",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["label", "value"],
        properties: {
          label: { type: "string" },
          value: { type: "string" },
        },
      },
    },
  },
};

const SYSTEM_PROMPT = `You extract data from photos of shop and restaurant receipts.

Copy values as printed. Use null for anything that isn't on the receipt; don't guess or calculate missing totals.
- items: one entry per purchased line, in printed order. Don't include subtotal, tax, tip or total lines as items. Discounts are items with a negative amount.
- currency: work it out from the symbol and the merchant's address or country (a bare "$" in Canada is CAD).
- purchase_date: read ambiguous dates like 03/04 using the merchant's country convention.
- extra: short label/value pairs for other details worth keeping. Leave it empty if there are none.`;

type ExtraField = { label: string; value: string };

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Use POST" }, 405);

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "Not signed in" }, 401);

  const body = await req.json().catch(() => ({}));
  const imagePath = body.image_path;
  if (typeof imagePath !== "string" || !imagePath.endsWith(".jpg")) {
    return json({ error: "image_path is required" }, 400);
  }

  // Acts as the signed-in user, so the storage policies only allow their own images.
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authHeader } },
  });

  const { data: file, error: downloadError } = await supabase.storage.from(BUCKET).download(imagePath);
  if (downloadError || !file) return json({ error: "Image not found" }, 404);

  const imageBase64 = encodeBase64(new Uint8Array(await file.arrayBuffer()));

  try {
    const response = await anthropic.messages.create({
      model: MODEL,
      max_tokens: 8000,
      system: SYSTEM_PROMPT,
      output_config: {
        effort: "low",
        format: { type: "json_schema", schema: RECEIPT_SCHEMA },
      },
      messages: [
        {
          role: "user",
          content: [
            { type: "image", source: { type: "base64", media_type: "image/jpeg", data: imageBase64 } },
            { type: "text", text: "Extract this receipt." },
          ],
        },
      ],
    });

    if (response.stop_reason === "refusal") {
      return json({ error: "This receipt couldn't be read. Enter the details by hand." }, 422);
    }
    if (response.stop_reason === "max_tokens") {
      return json({ error: "This receipt was too long to read in one go." }, 502);
    }

    const textBlock = response.content.find((b): b is Anthropic.TextBlock => b.type === "text");
    if (!textBlock) return json({ error: "No result from extraction" }, 502);

    const parsed = JSON.parse(textBlock.text);

    // extra comes back as [{label, value}]; the receipts.extra column stores a plain object.
    const extra = Object.fromEntries((parsed.extra as ExtraField[]).map((e) => [e.label, e.value]));

    return json({ ...parsed, extra, image_path: imagePath });
  } catch (err) {
    if (err instanceof Anthropic.RateLimitError) {
      return json({ error: "Too many scans at once. Try again in a minute." }, 429);
    }
    if (err instanceof Anthropic.APIError) {
      console.error("Claude API error", err.status, err.message);
      return json({ error: "Extraction failed. Try again." }, 502);
    }
    throw err;
  }
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
