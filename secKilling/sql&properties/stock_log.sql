CREATE TABLE `seckilling`.`stock_log`  (
  `stock_log_id` varchar(64) NOT NULL,
  `item_id` int(0) NOT NULL,
  `amount` int(0) NOT NULL DEFAULT 0,
  PRIMARY KEY (`stock_log_id`)
);
ALTER TABLE `seckilling`.`stock_log`
ADD COLUMN `status` int(0) NOT NULL DEFAULT 0 COMMENT '//1表示初始状态，2表示下单扣减库存成功，3表示下单回滚' AFTER `amount`;