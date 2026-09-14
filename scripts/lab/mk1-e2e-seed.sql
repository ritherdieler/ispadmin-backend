SET @lab_user_id = 900001;
SET @lab_sub_id = 900001;
SET @lab_pay_id = 900001;
SET @lab_ip = '192.168.250.1';
SET @lab_dni = '900001001';
SET @plan_id = (SELECT id FROM plan ORDER BY id LIMIT 1);
SET @place_id = (SELECT id FROM place ORDER BY id LIMIT 1);
SET @host_device_id = 1;
SET @pwd = (SELECT password FROM user WHERE username = 'dscorp' LIMIT 1);

INSERT INTO user (id, name, last_name, username, password, type, verified, email, phone, dni)
VALUES (
  @lab_user_id,
  'Lab',
  'Mk1 E2E',
  'labmk1',
  @pwd,
  'ADMIN',
  1,
  'labmk1-e2e@local.test',
  '900000001',
  '900001000'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  last_name = VALUES(last_name),
  password = VALUES(password),
  type = VALUES(type),
  verified = VALUES(verified);

INSERT INTO subscription (
  id,
  first_name,
  last_name,
  dni,
  ip,
  host_device_id,
  plan_id,
  place_id,
  service_status,
  client_type,
  installation_type,
  auto_cut,
  subscription_date_datetime,
  is_service_cut_off,
  phone,
  address,
  location,
  equipment_condition
)
VALUES (
  @lab_sub_id,
  'LAB MK1',
  'E2E FIXTURE',
  @lab_dni,
  @lab_ip,
  @host_device_id,
  @plan_id,
  @place_id,
  'ACTIVE',
  'PERSON',
  'FIBER',
  1,
  NOW(6),
  1,
  '900000001',
  'Fixture E2E Mikrotik MK1',
  '{"latitude": -11.233708313827057, "longitude": -77.37627815455198}',
  'LOAN'
)
ON DUPLICATE KEY UPDATE
  first_name = VALUES(first_name),
  last_name = VALUES(last_name),
  dni = VALUES(dni),
  ip = VALUES(ip),
  host_device_id = VALUES(host_device_id),
  plan_id = VALUES(plan_id),
  place_id = VALUES(place_id),
  service_status = VALUES(service_status),
  is_service_cut_off = VALUES(is_service_cut_off),
  location = VALUES(location),
  equipment_condition = VALUES(equipment_condition);

INSERT INTO payment (
  id,
  subscription_id,
  paid,
  amount_to_pay,
  discount_amount,
  discount_reason,
  method,
  amount_paid,
  billing_date_datetime,
  payment_date_datetime,
  is_payment_commit
)
VALUES (
  @lab_pay_id,
  @lab_sub_id,
  0,
  59.90,
  0,
  '',
  '',
  NULL,
  NOW(6),
  NULL,
  0
)
ON DUPLICATE KEY UPDATE
  subscription_id = VALUES(subscription_id),
  paid = 0,
  amount_to_pay = VALUES(amount_to_pay),
  discount_amount = 0,
  method = '',
  amount_paid = NULL,
  payment_date_datetime = NULL,
  billing_date_datetime = NOW(6);

SET @lab_sub_flow_id = 900002;
SET @lab_ip_flow = '192.168.250.2';
SET @lab_dni_flow = '900002001';

INSERT INTO subscription (
  id,
  first_name,
  last_name,
  dni,
  ip,
  host_device_id,
  plan_id,
  place_id,
  service_status,
  client_type,
  installation_type,
  auto_cut,
  subscription_date_datetime,
  is_service_cut_off,
  phone,
  address,
  location,
  equipment_condition
)
VALUES (
  @lab_sub_flow_id,
  'LAB MK1',
  'CANCEL REACT',
  @lab_dni_flow,
  @lab_ip_flow,
  @host_device_id,
  @plan_id,
  @place_id,
  'ACTIVE',
  'PERSON',
  'FIBER',
  0,
  '2024-06-01 10:00:00',
  0,
  '900000002',
  'Fixture cancel/reactivate MK1',
  '{"latitude": -11.233708313827057, "longitude": -77.37627815455198}',
  'LOAN'
)
ON DUPLICATE KEY UPDATE
  first_name = VALUES(first_name),
  last_name = VALUES(last_name),
  dni = VALUES(dni),
  ip = VALUES(ip),
  host_device_id = VALUES(host_device_id),
  plan_id = VALUES(plan_id),
  place_id = VALUES(place_id),
  service_status = 'ACTIVE',
  cancellation_date_datetime = NULL,
  is_service_cut_off = 0,
  subscription_date_datetime = '2024-06-01 10:00:00',
  location = VALUES(location),
  equipment_condition = VALUES(equipment_condition);

SET @lab_sub_migrate_id = 900003;
SET @lab_ip_migrate = '192.168.250.3';
SET @lab_dni_migrate = '900003001';

INSERT INTO subscription (
  id,
  first_name,
  last_name,
  dni,
  ip,
  host_device_id,
  plan_id,
  place_id,
  service_status,
  client_type,
  installation_type,
  auto_cut,
  subscription_date_datetime,
  is_service_cut_off,
  phone,
  address,
  location,
  equipment_condition
)
VALUES (
  @lab_sub_migrate_id,
  'LAB MK1',
  'MIGRATE SRC',
  @lab_dni_migrate,
  @lab_ip_migrate,
  @host_device_id,
  @plan_id,
  @place_id,
  'ACTIVE',
  'PERSON',
  'WIRELESS',
  0,
  '2024-05-01 10:00:00',
  0,
  '900000003',
  'Fixture wireless before fiber migration',
  '{"latitude": -11.233708313827057, "longitude": -77.37627815455198}',
  'LOAN'
)
ON DUPLICATE KEY UPDATE
  service_status = 'ACTIVE',
  installation_type = 'WIRELESS',
  cancellation_date_datetime = NULL,
  ip = VALUES(ip),
  host_device_id = VALUES(host_device_id),
  plan_id = VALUES(plan_id),
  subscription_date_datetime = '2024-05-01 10:00:00',
  is_service_cut_off = 0;
