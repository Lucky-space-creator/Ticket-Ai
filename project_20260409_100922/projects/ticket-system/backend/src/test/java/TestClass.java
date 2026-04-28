import com.ticket.service.StockReconciliationService;
import com.ticket.service.impl.StockReconciliationServiceImpl;
import com.ticket.task.StockReconciliationTask;
import org.junit.jupiter.api.Test;

public class TestClass {
    public static void main(String[] args) {
        System.out.println("Hello World!");
    }

    //测试自动对账功能
    @Test
    public void testReconcileAll() {
        StockReconciliationServiceImpl service = new StockReconciliationServiceImpl();
        service.reconcileAll();
    }
}
