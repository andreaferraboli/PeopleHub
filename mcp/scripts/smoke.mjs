// Verifica di un server MCP gia in linea, con un access token coniato (mint-token.mjs). Stampa solo conteggi e forme.
// Senza --write: solo letture. Con --write (SOLO su un uid di test dedicato, mai quello vero): crea una persona e un
// check-in, li rilegge, poi li marca deleted (tombstone) e controlla che spariscano dalle letture.
// Uso: MCP_TOKEN=<access token> node mcp/scripts/smoke.mjs [https://people-mcp.2-24-13-98.sslip.io/mcp] [--write]
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

const url = process.argv.find((a) => a.startsWith('http')) ?? 'https://people-mcp.2-24-13-98.sslip.io/mcp';
const write = process.argv.includes('--write');
const client = new Client({ name: 'smoke', version: '1.0.0' });
await client.connect(new StreamableHTTPClientTransport(new URL(url), { requestInit: { headers: { Authorization: `Bearer ${process.env.MCP_TOKEN}` } } }));
let fail = 0;
const check = (ok, label) => {
  console.log(`  ${ok ? 'ok' : 'KO'}  ${label}`);
  if (!ok) fail = 1;
};
const call = async (name, args = {}) => {
  const r = await client.callTool({ name, arguments: args });
  const text = r.content?.[0]?.text ?? '';
  if (r.isError) throw new Error(`${name}: ${text.slice(0, 200)}`);
  return JSON.parse(text);
};

const { tools } = await client.listTools();
check(tools.length === 25, `tools/list: ${tools.length} tool, ${tools.filter((t) => t.annotations?.readOnlyHint).length} di sola lettura`);
const profile = await call('get_profile');
console.log('  get_profile.counts:', JSON.stringify(profile.counts), '| fuso:', profile.timeZone, '| oggi:', profile.today);
const people = await call('list_people', { limit: 1 });
check(typeof people.total === 'number', `list_people: totale ${people.total}`);
for (const [name, args] of [['list_outings', { limit: 1 }], ['list_overdue_people', { limit: 1 }], ['upcoming_birthdays', { limit: 1 }], ['list_events', { limit: 1 }], ['list_reminders', { limit: 1 }], ['list_tags', {}], ['get_settings', {}]]) {
  const r = await call(name, args);
  check(true, `${name}: ${r.total ?? r.tags?.length ?? Object.keys(r).length}`);
}

if (write) {
  const created = await call('create_person', { firstName: 'MCP smoke', lastName: new Date().toISOString(), tags: ['smoke-test'] });
  check(/^[0-9a-f-]{36}$/.test(created.id), 'create_person: id UUID');
  const ci = await call('check_in', { personIds: [created.id], note: 'smoke test' });
  check(/^[0-9a-f-]{36}$/.test(ci.outingId), 'check_in: outingId UUID');
  const detail = await call('get_person', { id: created.id });
  check(detail.recentCheckIns?.length === 1 && detail.recentCheckIns[0].outingId === ci.outingId && detail.daysSinceLastCheckIn === 0, 'get_person: rilegge persona e check-in');
  const rawPerson = await call('raw_read', { path: `people/${created.id}` });
  check(rawPerson.data?.updatedBy === 'mcp' && rawPerson.data?.deleted === false && typeof rawPerson.data?.updatedAt === 'number' && typeof rawPerson.data?.serverUpdatedAt === 'string', 'documento con i campi di sync');
  await call('delete_outing', { outingId: ci.outingId });
  await call('delete_person', { id: created.id });
  const after = await call('list_people', { query: 'smoke', limit: 50 });
  check(!after.items.some((p) => p.id === created.id), 'dopo la cancellazione la persona non compare piu');
  const tomb = await call('raw_read', { path: `people/${created.id}` });
  const tombCi = await call('raw_read', { path: `checkIns/${ci.checkIns[0].id}` });
  check(tomb.data?.deleted === true && tombCi.data?.deleted === true, 'persona e check-in restano come tombstone (deleted: true)');
}
await client.close();
process.exit(fail);
