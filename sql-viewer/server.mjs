import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const IMG_DB_DIR = path.resolve(__dirname, '../backend/src/main/resources/imgDb');
const PORT = Number(process.env.SQL_VIEWER_PORT || 4100);
const DB_SERVER = process.env.SQL_SERVER || 'localhost';
const DB_NAME = process.env.SQL_DATABASE || 'O2OFoodPickupDB';

function runSqlJson(query) {
  return new Promise((resolve, reject) => {
    const wrapped = `SET NOCOUNT ON; ${query}`;
    execFile(
      'sqlcmd',
      ['-S', DB_SERVER, '-d', DB_NAME, '-E', '-C', '-f', '65001', '-y', '0', '-Q', wrapped],
      { maxBuffer: 20 * 1024 * 1024, encoding: 'utf8' },
      (err, stdout, stderr) => {
        if (err) {
          return reject(new Error(stderr || stdout || err.message));
        }
        const cleaned = stdout
          .replace(/^\uFEFF/, '')
          .split(/\r?\n/)
          .map((l) => l.trimEnd())
          .filter(
            (l) =>
              l.length > 0 &&
              !l.startsWith('Changed database context') &&
              !l.startsWith('JSON_F52E2B61') &&
              !/^-+$/.test(l.trim())
          )
          .join('');
        const start = cleaned.indexOf('[');
        const end = cleaned.lastIndexOf(']');
        if (start === -1 || end === -1 || end < start) return resolve([]);
        const jsonSlice = cleaned.slice(start, end + 1);
        try {
          resolve(JSON.parse(jsonSlice));
        } catch (parseErr) {
          reject(new Error(`JSON parse error: ${parseErr.message}\nRaw: ${jsonSlice.slice(0, 400)}`));
        }
      }
    );
  });
}

function getMimeType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  const map = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.png': 'image/png',
    '.webp': 'image/webp',
    '.svg': 'image/svg+xml',
  };
  return map[ext] || 'application/octet-stream';
}

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  });
  res.end(JSON.stringify(payload));
}

async function getOverviewData() {
  const [
    products,
    users,
    pickupPoints,
    lots,
    orders,
    trips,
    commissionLedger,
    recipes,
    knowledgeDocs,
    tables,
  ] = await Promise.all([
    runSqlJson(`
      SELECT
        p.id, p.sku_prefix, p.name, p.category, p.description, p.supplier_name,
        p.storage_type, p.min_shelf_life_days_at_pickup, p.dietary_tags, p.allergen_tags,
        p.image_url, p.is_active,
        pv.id AS variant_id, pv.sku, pv.pack_name, pv.pack_weight_grams, pv.unit_price,
        ISNULL((SELECT SUM(ib.available_qty) FROM dbo.inventory_balances ib WHERE ib.variant_id = pv.id), 0) AS total_available_qty
      FROM dbo.products p
      LEFT JOIN dbo.product_variants pv ON pv.product_id = p.id
      ORDER BY p.id
      FOR JSON PATH
    `),
    runSqlJson(`
      SELECT
        u.id, u.email, u.phone, u.full_name, u.avatar_url, u.status, u.created_at,
        r.code AS role_code
      FROM dbo.users u
      LEFT JOIN dbo.user_roles ur ON ur.user_id = u.id
      LEFT JOIN dbo.roles r ON r.id = ur.role_id
      ORDER BY u.id
      FOR JSON PATH
    `),
    runSqlJson(`
      SELECT
        pp.id, pp.code, pp.name, pp.address, pp.ward, pp.district, pp.city,
        pp.latitude, pp.longitude, pp.contact_phone, pp.cold_storage_capacity,
        pp.image_url, pp.status,
        u.id AS agent_user_id, u.full_name AS agent_name, u.phone AS agent_phone, u.avatar_url AS agent_avatar_url
      FROM dbo.pickup_points pp
      LEFT JOIN dbo.point_assignments pa ON pa.pickup_point_id = pp.id AND pa.active_to IS NULL
      LEFT JOIN dbo.users u ON u.id = pa.agent_user_id
      ORDER BY pp.id
      FOR JSON PATH
    `),
    runSqlJson(`
      SELECT
        l.id, l.lot_code, l.supplier_name, l.manufactured_date, l.expiry_date,
        l.initial_qty, l.remaining_qty, l.status,
        p.name AS product_name, pv.pack_name
      FROM dbo.lots l
      JOIN dbo.product_variants pv ON pv.id = l.variant_id
      JOIN dbo.products p ON p.id = pv.product_id
      ORDER BY l.expiry_date ASC
      FOR JSON PATH
    `),
    runSqlJson(`
      SELECT
        o.id, o.order_code, o.order_status, o.fulfillment_status, o.payment_status,
        o.total_amount, o.completion_type, o.has_open_issue, o.is_unclaimed_at_agent,
        o.cutoff_at, o.pickup_start_at, o.created_at,
        u.full_name AS customer_name, pp.name AS pickup_point_name
      FROM dbo.orders o
      JOIN dbo.users u ON u.id = o.customer_id
      JOIN dbo.pickup_points pp ON pp.id = o.pickup_point_id
      ORDER BY o.id DESC
      FOR JSON PATH
    `),
    runSqlJson(`
      SELECT
        t.id, t.trip_code, t.trip_type, t.status, t.departed_at, t.completed_at,
        u.full_name AS driver_name, u.avatar_url AS driver_avatar_url,
        oc.cycle_code
      FROM dbo.trips t
      JOIN dbo.users u ON u.id = t.driver_id
      JOIN dbo.order_cycles oc ON oc.id = t.cycle_id
      ORDER BY t.id
      FOR JSON PATH
    `),
    runSqlJson(`
      SELECT
        cl.id, cl.entry_type, cl.amount, cl.note, cl.created_at,
        o.order_code, pp.name AS pickup_point_name, cp.policy_code
      FROM dbo.commission_ledger cl
      JOIN dbo.orders o ON o.id = cl.order_id
      JOIN dbo.pickup_points pp ON pp.id = cl.pickup_point_id
      JOIN dbo.commission_policies cp ON cp.id = cl.policy_id
      ORDER BY cl.id DESC
      FOR JSON PATH
    `),
    runSqlJson(`
      SELECT id, name, servings, cooking_time_minutes, dietary_tags, instructions
      FROM dbo.recipes
      ORDER BY id
      FOR JSON PATH
    `),
    runSqlJson(`
      SELECT id, doc_code, version_tag, title, is_active, LEFT(content_markdown, 260) AS summary
      FROM dbo.knowledge_documents
      ORDER BY id
      FOR JSON PATH
    `),
    runSqlJson(`
      SELECT
        t.name AS table_name,
        SUM(p.rows) AS row_count
      FROM sys.tables t
      JOIN sys.partitions p ON t.object_id = p.object_id
      WHERE p.index_id IN (0, 1) AND t.name <> 'sysdiagrams'
      GROUP BY t.name
      ORDER BY t.name
      FOR JSON PATH
    `),
  ]);

  let imgFilesCount = 0;
  for (const sub of ['products', 'users', 'pickup-points']) {
    const dir = path.join(IMG_DB_DIR, sub);
    if (fs.existsSync(dir)) {
      imgFilesCount += fs.readdirSync(dir).filter((f) => /\.(jpg|jpeg|png|webp)$/i.test(f)).length;
    }
  }

  return {
    database: DB_NAME,
    server: DB_SERVER,
    imgDbPath: IMG_DB_DIR,
    imgFilesCount,
    checkedAt: new Date().toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }),
    products,
    users,
    pickupPoints,
    lots,
    orders,
    trips,
    commissionLedger,
    recipes,
    knowledgeDocs,
    tables,
  };
}

const server = http.createServer(async (req, res) => {
  try {
    const urlObj = new URL(req.url || '/', `http://127.0.0.1:${PORT}`);
    const pathname = decodeURIComponent(urlObj.pathname);

    if (pathname === '/' || pathname === '/index.html') {
      const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(html);
      return;
    }

    if (pathname.startsWith('/imgDb/')) {
      const rel = pathname.replace(/^\/imgDb\//, '');
      const fullPath = path.resolve(IMG_DB_DIR, rel);
      if (!fullPath.startsWith(IMG_DB_DIR) || !fs.existsSync(fullPath)) {
        res.writeHead(404);
        res.end('Image not found');
        return;
      }
      res.writeHead(200, {
        'Content-Type': getMimeType(fullPath),
        'Cache-Control': 'no-cache',
      });
      fs.createReadStream(fullPath).pipe(res);
      return;
    }

    if (pathname === '/api/overview') {
      const data = await getOverviewData();
      sendJson(res, 200, data);
      return;
    }

    if (pathname.startsWith('/api/table/')) {
      const tableName = pathname.replace('/api/table/', '');
      if (!/^[a-zA-Z0-9_]+$/.test(tableName)) {
        sendJson(res, 400, { error: 'Tên bảng không hợp lệ' });
        return;
      }
      const validTables = await runSqlJson(
        `SELECT name FROM sys.tables WHERE name = '${tableName}' FOR JSON PATH`
      );
      if (!Array.isArray(validTables) || validTables.length === 0) {
        sendJson(res, 404, { error: `Không tồn tại bảng ${tableName}` });
        return;
      }
      const rows = await runSqlJson(
        `SELECT TOP 100 * FROM dbo.[${tableName}] FOR JSON PATH, INCLUDE_NULL_VALUES`
      );
      sendJson(res, 200, { tableName, rows });
      return;
    }

    res.writeHead(404);
    res.end('Not Found');
  } catch (err) {
    sendJson(res, 500, { error: err?.message || 'Lỗi truy vấn SQL Server' });
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[SQL Viewer] Running at http://127.0.0.1:${PORT}/ (DB: ${DB_SERVER}/${DB_NAME})`);
  console.log(`[SQL Viewer] Serving imgDb from ${IMG_DB_DIR}`);
});
