-- Sample data for nureal_erp_schema.sql
SET search_path TO erp;

INSERT INTO company(name,tax_id) VALUES
('Nureal Tecnologia','12345678000199'),
('Acme Ltda','99888777000155');

INSERT INTO city(name,state,country) VALUES
('Rio de Janeiro','RJ','Brasil'),
('Niterói','RJ','Brasil'),
('São Paulo','SP','Brasil');

INSERT INTO person(company_id,person_type,name) VALUES
(1,'F','José Silva'),
(1,'F','Maria Souza'),
(2,'J','Fornecedor Alpha'),
(2,'J','Cliente Beta'),
(1,'F','Carlos Lima');

INSERT INTO person_physical(person_id,cpf,birth_date) VALUES
(1,'11111111111','1993-09-06'),
(2,'22222222222','1990-01-10'),
(5,'33333333333','1988-05-20');

INSERT INTO person_legal(person_id,cnpj,corporate_name) VALUES
(3,'11111111000199','Fornecedor Alpha Ltda'),
(4,'22222222000199','Cliente Beta S/A');

INSERT INTO address(person_id,city_id,street,number,zip_code) VALUES
(1,1,'Rua A','100','20000000'),
(2,2,'Rua B','200','24000000'),
(3,3,'Av Paulista','1000','01000000');

INSERT INTO phone(person_id,phone) VALUES
(1,'21999990001'),
(2,'21999990002'),
(3,'1133334444');

INSERT INTO email(person_id,email) VALUES
(1,'jose@nureal.com'),
(2,'maria@nureal.com'),
(3,'contato@alpha.com');

INSERT INTO customer(person_id,credit_limit) VALUES
(4,50000.00),
(1,3000.00);

INSERT INTO supplier(person_id) VALUES (3);

INSERT INTO employee(person_id,hire_date) VALUES
(1,'2024-01-10'),
(2,'2024-02-15');

INSERT INTO role(name) VALUES ('ADMIN'),('SALES');
INSERT INTO permission(name) VALUES ('READ'),('WRITE'),('DELETE');
INSERT INTO role_permission VALUES (1,1),(1,2),(1,3),(2,1);

INSERT INTO app_user(employee_id,username,password_hash) VALUES
(1,'admin','hash_admin'),
(2,'maria','hash_maria');

INSERT INTO user_role VALUES (1,1),(2,2);

INSERT INTO unit(name,symbol) VALUES
('Unidade','UN'),
('Quilograma','KG');

INSERT INTO category(name) VALUES
('Informática'),
('Periféricos');

INSERT INTO product(category_id,unit_id,sku,description,sale_price) VALUES
(1,1,'NOTE001','Notebook',4500),
(2,1,'MOUSE01','Mouse Gamer',120),
(2,1,'TEC001','Teclado Mecânico',350);

INSERT INTO warehouse(name) VALUES
('Matriz'),
('Filial');

INSERT INTO stock VALUES
(1,1,20),
(1,2,150),
(1,3,80),
(2,1,5);

INSERT INTO stock_movement(warehouse_id,product_id,movement_type,quantity) VALUES
(1,1,'I',20),
(1,2,'I',150),
(1,3,'I',80);

INSERT INTO purchase_order(supplier_id,order_date,status) VALUES
(1,'2026-07-01','OPEN');

INSERT INTO purchase_order_item(purchase_order_id,product_id,quantity,unit_price) VALUES
(1,1,10,3800),
(1,2,100,70);

INSERT INTO sales_order(customer_id,employee_id,order_date,status) VALUES
(1,1,'2026-07-20','PAID'),
(2,2,'2026-07-22','OPEN');

INSERT INTO sales_order_item(sales_order_id,product_id,quantity,unit_price,discount) VALUES
(1,1,1,4500,100),
(1,2,2,120,0),
(2,3,1,350,0);

INSERT INTO cost_center(name) VALUES
('Administrativo'),
('Comercial');

INSERT INTO bank_account(company_id,bank,agency,account_number) VALUES
(1,'Banco do Brasil','1234','98765-0');

INSERT INTO accounts_receivable(sales_order_id,due_date,amount) VALUES
(1,'2026-08-10',4640),
(2,'2026-08-15',350);

INSERT INTO accounts_payable(purchase_order_id,due_date,amount) VALUES
(1,'2026-08-05',45000);

INSERT INTO receipt(receivable_id,bank_account_id,payment_date,amount) VALUES
(1,1,'2026-07-25',4640);

INSERT INTO payment(payable_id,bank_account_id,payment_date,amount) VALUES
(1,1,'2026-07-28',45000);

INSERT INTO audit_log(table_name,operation,username) VALUES
('product','INSERT','admin'),
('sales_order','INSERT','admin');
