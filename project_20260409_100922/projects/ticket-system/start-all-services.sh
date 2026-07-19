#!/bin/bash
# ================================================================
#  购票智能客服系统 - Linux 启动脚本
# ================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志目录
LOG_DIR="./logs"
mkdir -p "$LOG_DIR"

# 服务列表：名称 端口 jar路径
declare -A SERVICES=(
    ["ticket-gateway"]="8080"
    ["user-service"]="8081"
    ["train-service"]="8082"
    ["order-service"]="8083"
    ["aichat-service"]="8084"
    ["customer-service"]="8085"
    ["admin-service"]="8086"
)

# JVM 参数
JAVA_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8"

# 检查 Java 版本
check_java() {
    if ! command -v java &> /dev/null; then
        echo -e "${RED}错误: 未找到 Java，请安装 JDK 17+${NC}"
        exit 1
    fi
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        echo -e "${RED}错误: Java 版本过低，需要 JDK 17+，当前版本: $JAVA_VERSION${NC}"
        exit 1
    fi
    echo -e "${GREEN}Java 版本检查通过: JDK $JAVA_VERSION${NC}"
}

# 检查基础设施服务
check_infra() {
    echo -e "${YELLOW}检查基础设施服务...${NC}"

    # 检查 MySQL
    if docker ps | grep -q ticket-mysql; then
        echo -e "${GREEN}  MySQL: 运行中${NC}"
    else
        echo -e "${RED}  MySQL: 未运行，请执行 docker compose up -d${NC}"
        exit 1
    fi

    # 检查 Redis
    if docker ps | grep -q ticket-redis; then
        echo -e "${GREEN}  Redis: 运行中${NC}"
    else
        echo -e "${RED}  Redis: 未运行，请执行 docker compose up -d${NC}"
        exit 1
    fi

    # 检查 Nacos
    if docker ps | grep -q ticket-nacos; then
        echo -e "${GREEN}  Nacos: 运行中${NC}"
    else
        echo -e "${RED}  Nacos: 未运行，请执行 docker compose up -d${NC}"
        exit 1
    fi

    # 检查 RocketMQ Namesrv
    if docker ps | grep -q ticket-rmq-namesrv; then
        echo -e "${GREEN}  RocketMQ Namesrv: 运行中${NC}"
    else
        echo -e "${RED}  RocketMQ Namesrv: 未运行，请执行 docker compose up -d${NC}"
        exit 1
    fi

    # 检查 ChromaDB（可选）
    if docker ps | grep -q ticket-chroma; then
        echo -e "${GREEN}  ChromaDB: 运行中${NC}"
    else
        echo -e "${YELLOW}  ChromaDB: 未运行（可选，AI 知识库功能需要）${NC}"
    fi
}

# 启动单个服务
start_service() {
    local service_name=$1
    local port=$2
    local jar_file="$service_name/target/$service_name-2.0.0.jar"
    local log_file="$LOG_DIR/$service_name.log"

    if [ ! -f "$jar_file" ]; then
        echo -e "${RED}  $service_name: JAR 文件不存在，请先执行 mvn clean package -DskipTests${NC}"
        return 1
    fi

    # 检查端口是否被占用
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo -e "${YELLOW}  $service_name: 端口 $port 已被占用，跳过${NC}"
        return 0
    fi

    echo -e "${GREEN}  $service_name: 启动中 (端口: $port)...${NC}"
    nohup java $JAVA_OPTS -jar "$jar_file" > "$log_file" 2>&1 &
    echo $! > "$LOG_DIR/$service_name.pid"
    echo -e "${GREEN}  $service_name: 已启动 (PID: $!)${NC}"
}

# 停止单个服务
stop_service() {
    local service_name=$1
    local pid_file="$LOG_DIR/$service_name.pid"

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            echo -e "${YELLOW}  $service_name: 停止中 (PID: $pid)...${NC}"
            kill "$pid"
            rm -f "$pid_file"
            echo -e "${GREEN}  $service_name: 已停止${NC}"
        else
            rm -f "$pid_file"
        fi
    fi
}

# 查看服务状态
status_services() {
    echo -e "${YELLOW}服务状态:${NC}"
    for service in "${!SERVICES[@]}"; do
        local port=${SERVICES[$service]}
        local pid_file="$LOG_DIR/$service.pid"

        if [ -f "$pid_file" ]; then
            local pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                echo -e "${GREEN}  $service: 运行中 (PID: $pid, 端口: $port)${NC}"
            else
                echo -e "${RED}  $service: 已停止 (端口: $port)${NC}"
                rm -f "$pid_file"
            fi
        else
            echo -e "${RED}  $service: 未启动 (端口: $port)${NC}"
        fi
    done
}

# 主菜单
case "${1:-}" in
    start)
        echo "================================================"
        echo "  购票智能客服系统 - 启动服务"
        echo "================================================"
        check_java
        check_infra
        echo ""
        echo "启动微服务..."
        for service in ticket-gateway user-service train-service order-service aichat-service customer-service admin-service; do
            start_service "$service" "${SERVICES[$service]}"
            sleep 3
        done
        echo ""
        echo "================================================"
        echo "  所有服务已启动！"
        echo "================================================"
        status_services
        ;;
    stop)
        echo "================================================"
        echo "  购票智能客服系统 - 停止服务"
        echo "================================================"
        for service in "${!SERVICES[@]}"; do
            stop_service "$service"
        done
        echo -e "${GREEN}所有服务已停止${NC}"
        ;;
    restart)
        $0 stop
        sleep 3
        $0 start
        ;;
    status)
        status_services
        ;;
    logs)
        if [ -z "${2:-}" ]; then
            echo "用法: $0 logs <服务名>"
            echo "可用服务: ${!SERVICES[*]}"
            exit 1
        fi
        tail -f "$LOG_DIR/$2.log"
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status|logs <服务名>}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动所有微服务"
        echo "  stop    - 停止所有微服务"
        echo "  restart - 重启所有微服务"
        echo "  status  - 查看服务状态"
        echo "  logs    - 查看服务日志"
        exit 1
        ;;
esac
