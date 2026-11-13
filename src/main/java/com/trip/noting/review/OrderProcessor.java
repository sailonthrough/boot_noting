// package com.trip.noting.review;
//
// import org.slf4j.LoggerFactory;
//
// import java.util.logging.Logger;
//
// public class OrderProcessor {
//     private final OrderRepository orderRepo;
//     private final UserRepository userRepo;
//     private final ProductRepository productRepo;
//     private final NotificationService notificationService;
//     private final Logger logger = LoggerFactory.getLogger(OrderProcessor.class);
//
//     public OrderProcessor(OrderRepository orderRepo, UserRepository userRepo,
//                           ProductRepository productRepo,
//                           NotificationService notificationService) {
//         this.orderRepo = orderRepo;
//         this.userRepo = userRepo;
//         this.productRepo = productRepo;
//         this.notificationService = notificationService;
//     }
//
//     public synchronized List<OrderResult> processOrders(List<Long> orderIds) {
//         // TODO 问题一：参数判断 orderIds 是否为空和ID是否是0
//         List<OrderResult> results = new ArrayList<>();
//         // TODO 问题二
//         // 数据库中循环查询（订单、用户信息）数据库有性能隐患——建议批量查询，如果orderIds 特别大 分批操作 而不是每次根据ID查询
//         for (Long orderId : orderIds) {
//             try {
//                 Order order = orderRepo.findById(orderId);
//                 if (order == null) {
//                     results.add(new OrderResult(orderId, false, "Order not found"));
//                     continue;
//                 }
//
//                 User user = userRepo.findById(order.getUserId());
//                 if (user == null) {
//                     results.add(new OrderResult(orderId, false, "User not found"));
//                     continue;
//                 }
//
//                 boolean allInStock = true;
//                 // TODO 问题三
//                 // 内存判断剩余库存存在巨大隐患，应该使用分布式锁或者在SQL层面使用乐观锁
//                 // 如果使用SQL乐观锁：where条件中至少要有版本号和剩余库存判断
//                 for (OrderItem item : order.getItems()) {
//                     Product product = productRepo.findById(item.getProductId());
//                     if (product == null || product.getStock() < item.getQuantity()) {
//                         allInStock = false;
//                         break;
//                     }
//                 }
//                 if (!allInStock) {
//                     results.add(new OrderResult(orderId, false, "Insufficient stock"));
//                     continue;
//                 }
//
//                 PriceCalculationStrategy strategy = PriceStrategyFactory.getStrategy(user);
//                 BigDecimal totalPrice = strategy.calculate(order);
//                 order.setTotalPrice(totalPrice);
//                 // TODO 问题四：此处依然是循环内操作数据库
//                 // TODO 问题五：此处如果有乐观锁或者分布式锁的情况下要考虑只有部分SKU库存扣减失败回滚数据的操作
//                 // TODO 分布式锁部分扣减成功涉及已扣减库存释放库存  乐观锁考虑事务回滚的方式
//                 for (OrderItem item : order.getItems()) {
//                     Product product = productRepo.findById(item.getProductId());
//                     // TODO 问题六 此处有超卖的可能——并发导致
//                     product.setStock(product.getStock() - item.getQuantity());
//                     productRepo.save(product);
//                 }
//
//                 order.setStatus("CONFIRMED");
//                 orderRepo.save(order);
//
//                 notificationService.send(user.getEmail(),
//                     "Order Confirmed",
//                     "Your order #" + orderId + " total: $" + totalPrice);
//
//                 logger.info("Order {} processed for user {}", orderId, user.getId());
//                 results.add(new OrderResult(orderId, true, "Success"));
//
//             } catch (Exception e) {
//                 logger.error("Failed to process order {}", orderId, e);
//                 results.add(new OrderResult(orderId, false, e.getMessage()));
//             }
//         }
//
//         return results;
//     }
// }