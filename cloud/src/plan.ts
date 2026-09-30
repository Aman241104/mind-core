// Key content from the "MS Abroad Plan" doc, seeded once from the laptop so the app can show it
// natively instead of linking out to a browser.
import type { Env } from "./index.ts";

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

export async function listPlanActions(env: Env): Promise<Response> {
  const rows = await env.DB.prepare("SELECT * FROM plan_actions ORDER BY order_hint").all();
  return json({ actions: rows.results });
}

export async function listPlanUniversities(env: Env): Promise<Response> {
  const rows = await env.DB.prepare("SELECT * FROM plan_universities ORDER BY rank").all();
  return json({ universities: rows.results });
}

export async function toggleWishlist(id: string, env: Env): Promise<Response> {
  const row = await env.DB.prepare("SELECT wishlisted FROM plan_universities WHERE id = ?").bind(id).first<{ wishlisted: number }>();
  if (!row) return new Response(JSON.stringify({ error: "no such university" }), { status: 404 });
  const next = row.wishlisted ? 0 : 1;
  await env.DB.prepare("UPDATE plan_universities SET wishlisted = ? WHERE id = ?").bind(next, id).run();
  return json({ wishlisted: next === 1 });
}

export async function listPlanMarket(env: Env): Promise<Response> {
  const rows = await env.DB.prepare("SELECT * FROM plan_market ORDER BY order_hint").all();
  return json({ market: rows.results });
}

export async function listPlanScholarships(env: Env): Promise<Response> {
  const rows = await env.DB.prepare("SELECT * FROM plan_scholarships ORDER BY order_hint").all();
  return json({ scholarships: rows.results });
}

type PlanAction = { when_text: string; action: string; why?: string };
type PlanUniversity = { rank: number; name: string; country: string; tuition?: string; scholarship?: string; why_fits?: string };
type PlanMarket = { country: string; post_study_visa: string; outlook: string; source_note?: string };
type PlanScholarship = { name: string; place: string; amount?: string; eligibility?: string; deadline?: string };

/** POST /v1/brain/plan/seed — one-off bulk load, run from the laptop, not the phone. */
export async function seedPlan(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as {
    actions?: PlanAction[]; universities?: PlanUniversity[]; market?: PlanMarket[]; scholarships?: PlanScholarship[];
  };
  let addedActions = 0;
  for (const a of body.actions ?? []) {
    await env.DB.prepare(
      "INSERT INTO plan_actions (id, when_text, action, why, order_hint) VALUES (?, ?, ?, ?, ?)",
    ).bind(crypto.randomUUID(), a.when_text, a.action, a.why ?? null, addedActions).run();
    addedActions++;
  }
  let addedUnis = 0;
  for (const u of body.universities ?? []) {
    await env.DB.prepare(
      "INSERT INTO plan_universities (id, rank, name, country, tuition, scholarship, why_fits) VALUES (?, ?, ?, ?, ?, ?, ?)",
    ).bind(crypto.randomUUID(), u.rank, u.name, u.country, u.tuition ?? null, u.scholarship ?? null, u.why_fits ?? null).run();
    addedUnis++;
  }
  let addedMarket = 0;
  for (const m of body.market ?? []) {
    await env.DB.prepare(
      "INSERT INTO plan_market (id, country, post_study_visa, outlook, source_note, order_hint) VALUES (?, ?, ?, ?, ?, ?)",
    ).bind(crypto.randomUUID(), m.country, m.post_study_visa, m.outlook, m.source_note ?? null, addedMarket).run();
    addedMarket++;
  }
  let addedScholarships = 0;
  for (const s of body.scholarships ?? []) {
    await env.DB.prepare(
      "INSERT INTO plan_scholarships (id, name, place, amount, eligibility, deadline, order_hint) VALUES (?, ?, ?, ?, ?, ?, ?)",
    ).bind(crypto.randomUUID(), s.name, s.place, s.amount ?? null, s.eligibility ?? null, s.deadline ?? null, addedScholarships).run();
    addedScholarships++;
  }
  return json({ addedActions, addedUnis, addedMarket, addedScholarships });
}
