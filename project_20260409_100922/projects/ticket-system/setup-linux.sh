#!/bin/bash
# ================================================================
#  购票智能客服系统 - Linux 一键部署脚本
# ================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "================================================"
echo "  购票智能客服系统 - 一键部署"
echo "================================================"
echo ""

# 1. 检查 Docker
echo -e "${YELLOW}[1/6] 检查 Docker...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker 未安装，正在安装...${NC}"
    sudo apt update
    sudo apt install -y docker.io docker-compose-plugin
    sudo systemctl start docker
    sudo systemctl enable docker
    sudo usermod -aG docker $USER
    echo -e "${YELLOW}Docker 已安装，请重新登录以使 docker 组生效，然后重新运行此脚本${NC}"
    exit 0
fi
echo -e "${GREEN}Docker 已安装${NC}"

# 2. 启动基础设施
echo -e "${YELLOW}[2/6] 启动基础设施服务...${NC}"
docker compose up -d
echo -e "${GREEN}基础设施服务已启动${NC}"

# 3. 启动 ChromaDB
echo -e "${YELLOW}[3/6] 启动 ChromaDB...${NC}"
if docker ps | grep -q ticket-chroma; then
    echo -e "${GREEN}ChromaDB 已在运行${NC}"
else
    docker run -d \
        --name ticket-chroma \
        -p 8000:8000 \
        --restart unless-stopped \
        chromadb/chroma:latest
    echo -e "${GREEN}ChromaDB 已启动${NC}"
fi

# 4. 等待 MySQL 就绪并初始化数据库
echo -e "${YELLOW}[4/6] 初始化数据库...${NC}"
echo "等待 MySQL 启动..."
for i in {1..60}; do
    if docker exec ticket-mysql mysqladmin ping -h"localhost" -uroot -proot --silent 2>/dev/null; then
        echo -e "${GREEN}MySQL 已就绪${NC}"
        break
    fi
    sleep 2
done

# 执行初始化脚本
echo "执行数据库初始化..."
docker exec -i ticket-mysql mysql -uroot -proot < database/init.sql 2>/dev/null || true
echo -e "${GREEN}数据库初始化完成${NC}"

# 5. 编译项目
echo -e "${YELLOW}[5/6] 编译项目...${NC}"
mvn clean package -DskipTests -q
echo -e "${GREEN}编译完成${NC}"

# 6. 启动微服务
echo -e "${YELLOW}[6/6] 启动微服务...${NC}"
chmod +x start-all-services.sh
./start-all-services.sh start

echo ""
echo "================================================"
echo "  部署完成！"
echo "================================================"
echo ""
echo "服务地址:"
echo "  Gateway:      http://localhost:8080"
echo "  User:         http://localhost:8081"
echo "  Train:        http://localhost:8082"
echo "  Order:        http://localhost:8083"
echo "  AI Chat:      http://localhost:8084"
echo "  Customer:     http://localhost:8085"
echo "  Admin:        http://localhost:8086"
echo ""
echo "管理工具:"
echo "  Nacos:        http://localhost:8848/nacos (nacos/nacos)"
echo "  MySQL:        localhost:3306 (root/root)"
echo "  Redis:        localhost:6379"
echo "  ChromaDB:     http://localhost:8000"
echo ""
echo "常用命令:"
echo "  查看状态: ./start-all-services.sh status"
echo "  查看日志: ./start-all-services.sh logs <服务名>"
echo "  停止服务: ./start-all-services.sh stop"
echo "  重启服务: ./start-all-services.sh restart"
