package burp;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BurpExtender implements IBurpExtender, ITab, IContextMenuFactory {
    private CompeteGUI competeGUI;
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private final List<Future<?>> futures = Collections.synchronizedList(new ArrayList<>());
    private ScheduledExecutorService scheduler;
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        this.competeGUI = new CompeteGUI(helpers);

        callbacks.setExtensionName("RaceCondition Tester");
        callbacks.addSuiteTab(this);
        callbacks.registerContextMenuFactory(this);

        // 绑定按钮事件
        competeGUI.getSendButton().addActionListener(e -> startAttack());
        competeGUI.getStopButton().addActionListener(e -> stopAttack());
        competeGUI.getClearButton().addActionListener(e -> clearAllRequests());
        competeGUI.getSingleConcurrentButton().addActionListener(e -> startSingleConcurrent());
        competeGUI.getClearLogButton().addActionListener(e -> clearLog());

        // 初始化线程池
        scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
        
        // 添加插件卸载时的清理
        callbacks.registerExtensionStateListener(() -> {
            stopAttack();
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        });
    }

    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        List<JMenuItem> items = new ArrayList<>();
        JMenuItem sendItem = new JMenuItem("RaceCondition Tester");

        sendItem.addActionListener(e -> {
            IHttpRequestResponse[] messages = invocation.getSelectedMessages();
            if (messages != null && messages.length > 0) {
                competeGUI.setBaseService(messages[0].getHttpService());
                for (IHttpRequestResponse message : messages) {
                    competeGUI.addRequest(message.getRequest());
                }
            }
        });

        items.add(sendItem);
        return items;
    }

    private void startAttack() {
        if (isRunning.get()) {
            showMessage("当前攻击正在进行中", "警告", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<byte[]> requests = competeGUI.getRequestList();
        if (requests.isEmpty()) {
            showMessage("请通过右键菜单添加请求", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double delay;
        try {
            delay = Double.parseDouble(competeGUI.getDelay());
        } catch (NumberFormatException e) {
            showMessage("延迟必须为数字", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        isRunning.set(true);
        competeGUI.toggleButtons(false);
        competeGUI.clearResponseDisplay();

        CountDownLatch latch = new CountDownLatch(requests.size());

        for (int i = 0; i < requests.size(); i++) {
            final int requestId = i + 1;
            final byte[] request = requests.get(i);

            // 计算延时：i * delay，转换为 long 毫秒值
            long delayMillis = (long) (i * delay);

            Future<?> future = scheduler.schedule(() -> {
                try {
                    // 设置超时时间为30秒
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<IHttpRequestResponse> task = executor.submit(() -> 
                        callbacks.makeHttpRequest(competeGUI.getBaseService(), request));
                    IHttpRequestResponse response = task.get(30, TimeUnit.SECONDS);
                    logResponse(response, requestId);
                } catch (TimeoutException e) {
                    logError("请求超时", requestId);
                } catch (Exception e) {
                    logError(e.getMessage(), requestId);
                } finally {
                    latch.countDown();
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
            futures.add(future);  // 保存任务，方便后续取消
        }

        new Thread(() -> {
            try {
                latch.await();
                SwingUtilities.invokeLater(() -> {
                    isRunning.set(false);
                    competeGUI.toggleButtons(true);
                    competeGUI.appendResponse("\n[系统] 所有请求已完成\n");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void stopAttack() {
        isRunning.set(false);
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        futures.clear();
        competeGUI.toggleButtons(true);
        competeGUI.appendResponse("\n[系统] 攻击已停止\n");
    }

    private void clearAllRequests() {
        synchronized (futures) {
            futures.clear();
        }
        competeGUI.clearAll();
        isRunning.set(false);
        competeGUI.toggleButtons(true);
    }

    /**
     * 修改后的 logResponse 方法：
     * 1. 解析响应体
     * 2. 如果响应头中包含 "application/json"，则格式化输出 JSON（缩进4格）
     */
    private void logResponse(IHttpRequestResponse response, int requestId) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = TIME_FORMATTER.format(LocalDateTime.now());
            try {
                String statusCode = "无响应";
                String body = "";

                if (response != null && response.getResponse() != null) {
                    IResponseInfo respInfo = helpers.analyzeResponse(response.getResponse());
                    statusCode = String.valueOf(respInfo.getStatusCode());
                    byte[] responseBytes = response.getResponse();
                    int bodyOffset = respInfo.getBodyOffset();
                    body = new String(Arrays.copyOfRange(responseBytes, bodyOffset, responseBytes.length));

                    // 判断响应头中是否包含 application/json
                    List<String> headers = respInfo.getHeaders();
                    boolean isJson = false;
                    for (String header : headers) {
                        if (header.toLowerCase().contains("content-type") &&
                                header.toLowerCase().contains("application/json")) {
                            isJson = true;
                            break;
                        }
                    }
                    if (isJson) {
                        try {
                            Object json = new JSONTokener(body).nextValue();
                            if (json instanceof JSONObject) {
                                body = ((JSONObject) json).toString(4);
                            } else if (json instanceof JSONArray) {
                                body = ((JSONArray) json).toString(4);
                            }
                        } catch (Exception e) {
                            // 格式化失败则保持原样
                        }
                    }
                }

                competeGUI.appendResponse(
                        String.format("[请求%d][%s] 状态码:%s\n%s\n",
                                requestId, timestamp, statusCode, body)
                );
            } catch (Exception e) {
                competeGUI.appendResponse(
                        String.format("[请求%d][%s] 响应解析异常\n", requestId, timestamp)
                );
            }
        });
    }

    private void logError(String message, int requestId) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = TIME_FORMATTER.format(LocalDateTime.now());
            String stackTrace = "";
            if (message != null && message.contains("Exception")) {
                stackTrace = "\n详细错误信息：" + message;
            }
            competeGUI.appendResponse(
                    String.format("[请求%d][%s] 错误: %s%s\n", 
                        requestId, timestamp, message, stackTrace)
            );
        });
    }

    private void showMessage(String content, String title, int type) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(competeGUI.getPanel(), content, title, type)
        );
    }

    @Override
    public String getTabCaption() {
        return "RaceCondition Tester";
    }

    @Override
    public Component getUiComponent() {
        return competeGUI.getPanel();
    }

    private void startSingleConcurrent() {
        if (isRunning.get()) {
            showMessage("当前攻击正在进行中", "警告", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<byte[]> selectedRequests = competeGUI.getSelectedRequests();
        if (selectedRequests.isEmpty()) {
            showMessage("请选择至少一个请求", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!competeGUI.isValidConcurrentCount()) {
            showMessage("并发次数必须为非负整数", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double delay;
        try {
            delay = Double.parseDouble(competeGUI.getDelay());
        } catch (NumberFormatException e) {
            showMessage("延迟必须为数字", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        isRunning.set(true);
        competeGUI.toggleButtons(false);
        competeGUI.clearResponseDisplay();

        String concurrentCount = competeGUI.getConcurrentCount();
        int maxCount = concurrentCount.trim().isEmpty() || concurrentCount.equals("0") ? 
                      Integer.MAX_VALUE : Integer.parseInt(concurrentCount);

        // 为每个选中的请求创建一个计数器
        int totalRequests = selectedRequests.size() * (maxCount == Integer.MAX_VALUE ? 1 : maxCount);
        CountDownLatch completionLatch = new CountDownLatch(totalRequests);

        // 创建一个新的线程来控制发包
        Thread senderThread = new Thread(() -> {
            List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();
            try {
                // 为每个选中的请求创建任务
                for (int requestIndex = 0; requestIndex < selectedRequests.size(); requestIndex++) {
                    final byte[] currentRequest = selectedRequests.get(requestIndex);
                    final int finalRequestIndex = requestIndex + 1;

                    int count = 0;
                    while (isRunning.get() && count < maxCount) {
                        final int currentCount = count + 1;
                        
                        ScheduledFuture<?> future = scheduler.schedule(() -> {
                            if (!isRunning.get()) {
                                return;
                            }
                            try {
                                ExecutorService executor = Executors.newSingleThreadExecutor();
                                Future<IHttpRequestResponse> task = executor.submit(() -> 
                                    callbacks.makeHttpRequest(competeGUI.getBaseService(), currentRequest));
                                
                                IHttpRequestResponse response = task.get(30, TimeUnit.SECONDS);
                                executor.shutdown();
                                
                                if (isRunning.get()) {
                                    // 修改日志格式，显示是哪个请求的第几次并发
                                    String requestInfo = selectedRequests.size() > 1 ? 
                                        String.format("请求%d-%d", finalRequestIndex, currentCount) :
                                        String.format("请求-%d", currentCount);
                                    logResponse(response, requestInfo);
                                }
                            } catch (TimeoutException e) {
                                if (isRunning.get()) {
                                    logError("请求超时", String.format("请求%d-%d", finalRequestIndex, currentCount));
                                }
                            } catch (Exception e) {
                                if (isRunning.get()) {
                                    logError(e.getMessage(), String.format("请求%d-%d", finalRequestIndex, currentCount));
                                }
                            } finally {
                                completionLatch.countDown();
                            }
                        }, (long)(count * delay), TimeUnit.MILLISECONDS);
                        
                        scheduledTasks.add(future);
                        futures.add(future);
                        count++;

                        if (!isRunning.get()) {
                            break;
                        }
                    }
                }

                // 等待所有请求完成或停止信号
                if (isRunning.get()) {
                    try {
                        // 设置一个合理的超时时间
                        long timeoutMillis = (long)((maxCount - 1) * delay) + 30000;
                        boolean completed = completionLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
                        
                        if (completed && isRunning.get()) {
                            SwingUtilities.invokeLater(() -> {
                                competeGUI.appendResponse("\n[系统] 所有请求已完成\n");
                            });
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                SwingUtilities.invokeLater(() -> {
                    isRunning.set(false);
                    competeGUI.toggleButtons(true);
                });
            }
        });

        senderThread.start();
    }

    private void logResponse(IHttpRequestResponse response, String requestInfo) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = TIME_FORMATTER.format(LocalDateTime.now());
            try {
                String statusCode = "无响应";
                String body = "";

                if (response != null && response.getResponse() != null) {
                    IResponseInfo respInfo = helpers.analyzeResponse(response.getResponse());
                    statusCode = String.valueOf(respInfo.getStatusCode());
                    byte[] responseBytes = response.getResponse();
                    int bodyOffset = respInfo.getBodyOffset();
                    body = new String(Arrays.copyOfRange(responseBytes, bodyOffset, responseBytes.length));

                    List<String> headers = respInfo.getHeaders();
                    boolean isJson = false;
                    for (String header : headers) {
                        if (header.toLowerCase().contains("content-type") &&
                                header.toLowerCase().contains("application/json")) {
                            isJson = true;
                            break;
                        }
                    }
                    if (isJson) {
                        try {
                            Object json = new JSONTokener(body).nextValue();
                            if (json instanceof JSONObject) {
                                body = ((JSONObject) json).toString(4);
                            } else if (json instanceof JSONArray) {
                                body = ((JSONArray) json).toString(4);
                            }
                        } catch (Exception e) {
                            // 格式化失败则保持原样
                        }
                    }
                }

                competeGUI.appendResponse(
                        String.format("[%s][%s] 状态码:%s\n%s\n",
                                requestInfo, timestamp, statusCode, body)
                );
            } catch (Exception e) {
                competeGUI.appendResponse(
                        String.format("[%s][%s] 响应解析异常\n", requestInfo, timestamp)
                );
            }
        });
    }

    private void logError(String message, String requestInfo) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = TIME_FORMATTER.format(LocalDateTime.now());
            String stackTrace = "";
            if (message != null && message.contains("Exception")) {
                stackTrace = "\n详细错误信息：" + message;
            }
            competeGUI.appendResponse(
                    String.format("[%s][%s] 错误: %s%s\n", 
                        requestInfo, timestamp, message, stackTrace)
            );
        });
    }

    private void clearLog() {
        SwingUtilities.invokeLater(() -> {
            competeGUI.clearResponseDisplay();
            competeGUI.appendResponse("[系统] 日志已清空\n");
        });
    }
}
