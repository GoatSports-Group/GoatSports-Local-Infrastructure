# Self-hosted MongoDB Search và Vector Search

Thư mục này cung cấp MongoDB Community Server cùng MongoDB Community Search (`mongot`)
cho GOAT Sports mà không phụ thuộc MongoDB Atlas hoặc Kubernetes.

- `local`: chạy development bằng Docker Desktop.
- `production`: chạy toàn bộ MongoDB Search trên một VPS Linux.
- Vector index: `embedding_vector_index`, trường `vector`, 256 chiều, cosine similarity và
  filter theo `sourceType`.

Ứng dụng chỉ kết nối đến MongoDB. `mongod` tự chuyển truy vấn Search sang `mongot` qua
gRPC trong Docker network.

## Phiên bản đã pin

- MongoDB Community Server: `8.3.9-ubi9-slim`
- MongoDB Community Search: `1.70.3`

Không dùng tag `latest` cho production. Kiểm tra compatibility trước khi nâng một trong
hai image.

## Production một VPS

Production sử dụng một replica-set node và một `mongot`:

```text
AI / Audit / Social services
             |
             v
 mongodb-search:27017
             |
             v
 mongodb-search-engine:27028
```

MongoDB không được public ra Internet. Port quản trị chỉ bind vào
`127.0.0.1:27018` và được truy cập từ xa qua SSH tunnel.

Stack một VPS không có high availability. Phải lưu backup ở hệ thống khác VPS để có thể
khôi phục khi máy hoặc disk bị hỏng.

Hướng dẫn đầy đủ nằm tại
`explain/mongodb-search-development-and-production.md`.
