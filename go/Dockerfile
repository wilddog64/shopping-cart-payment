FROM golang:1.21 AS build

WORKDIR /src
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/payment-service ./cmd/server

FROM gcr.io/distroless/static-debian12:nonroot
WORKDIR /
COPY --from=build /out/payment-service /payment-service
EXPOSE 8084
USER nonroot:nonroot
ENTRYPOINT ["/payment-service"]
