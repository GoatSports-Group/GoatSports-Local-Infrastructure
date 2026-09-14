# Self-managed MongoDB Search and Vector Search

Thư mục này cung cấp cùng một nền tảng Search cho development và production mà
không phụ thuộc MongoDB Atlas hoặc Kubernetes.

- `local`: một MongoDB replica-set node và một `mongot`, chạy bằng Docker Compose.
- `production`: ba MongoDB node, tối thiểu hai `mongot` và Envoy L7 trên các VM Linux.
- Vector index luôn có tên `embedding_vector_index`, trường `vector`, 256 chiều,
  cosine similarity và filter theo `sourceType`.

Ứng dụng chỉ kết nối đến `mongod`. Không cấu hình `ai-service` kết nối trực tiếp
đến gRPC, health hoặc metrics port của `mongot`.

## Phiên bản đã pin

- MongoDB Community Server: `8.3.9-ubi9-slim`
- MongoDB Community Search: `1.70.3`

Luôn kiểm tra compatibility matrix trước khi nâng một trong hai image. Không đổi
sang tag `latest` trong production.

## Migration từ MongoDB 7

MongoDB 7 hiện tại là standalone và không thể chạy `$vectorSearch`. Không mount
volume cũ trực tiếp vào MongoDB 8.3. Quy trình an toàn:

1. Dừng ghi dữ liệu vào MongoDB 7.
2. Tạo `mongodump` từ database cần chuyển.
3. Khởi động stack Search mới bằng volume trống.
4. `mongorestore` vào replica set mới.
5. Đợi `embedding_vector_index` đạt `READY` và `queryable: true`.
6. Chạy smoke test semantic search.
7. Đổi `AI_MONGODB_URI` rồi restart `ai-service`.
8. Chỉ xóa volume cũ sau khi đã xác nhận backup và rollback.

Local fallback cosine trong `ai-service` vẫn được giữ để giảm ảnh hưởng khi
`mongot` đang rebuild index hoặc tạm thời không khả dụng.