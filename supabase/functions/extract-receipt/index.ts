import Anthropic from "npm:@anthropic-ai/sdk@^1.3.0";
import { createClient } from "npm:@supabase/supabase-js@2";
import { encodeBase64 } from "jsr:@std/encoding@1/base64";

const BUCKET = "receipt-images";
const MODEL = "claude-haiku-5-5";

// Reads ANTHROPIC_API_KEY from the function's secrets.
const anthropic = new Anthropic();

const nullable = (schema: Record<string, unknown>) => ({ anyOf: [schema, { type: "null" }] });

// Field names match the receipts columns. Only the date and amount come off the receipt; the app
// asks for the nature of expense and company, and fills in the claimant's name.
const RECEIPT_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["expense_date", "amount"],
  properties: {
    expense_date: nullable({ type: "string", format: "date" }),
    amount: nullable({ type: "number" }),
  },
};

const SYSTEM_PROMPT = `You read the date and the amount paid from photos of receipts and card slips, for a South African expense claim.

- expense_date: the date of the purchase. Receipts here usually print the day first, so 03/04/2026 is 3 April 2026.
- amount: the final amount paid in Rand, including a tip if one is printed or written on the slip. Use the grand total, not a subtotal or the VAT line.
Use null for a value you can't read. Don't guess.`;

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
      max_tokens: 1000,
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
      return json({ error: "Extraction failed. Try again." }, 502);
    }

    const textBlock = response.content.find((b): b is Anthropic.TextBlock => b.type === "text");
    if (!textBlock) return json({ error: "No result from extraction" }, 502);

    const parsed = JSON.parse(textBlock.text);
    return json({ expense_date: parsed.expense_date, amount: parsed.amount, image_path: imagePath });
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
