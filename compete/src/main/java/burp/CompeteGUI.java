package burp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class CompeteGUI {
    private JPanel panel;
    private JTable requestTable;
    private DefaultTableModel tableModel;
    private JTextArea requestDisplayArea;
    private JTextArea responseDisplayArea;
    private JTextField delayField;
    private JTextField concurrentCountField;
    private JButton sendButton;
    private JButton stopButton;
    private JButton clearButton;
    private JButton moveUpButton;
    private JButton deleteButton;
    private JButton singleConcurrentButton;
    private JButton clearLogButton;
    private IHttpService baseService;
    private final List<byte[]> rawRequests = new ArrayList<>();
    private IExtensionHelpers helpers;

    public CompeteGUI(IExtensionHelpers helpers) {
        this.helpers = helpers;
        initComponents();
    }

    private void initComponents() {
        panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 控制面板
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        delayField = new JTextField("0", 8);
        concurrentCountField = new JTextField("0", 8);
        sendButton = new JButton("开始攻击");
        stopButton = new JButton("停止");
        clearButton = new JButton("清空");
        moveUpButton = new JButton("↑");
        deleteButton = new JButton("×");
        singleConcurrentButton = new JButton("单包并发");
        clearLogButton = new JButton("清空日志");
        stopButton.setEnabled(false);

        // 按钮事件绑定
        moveUpButton.addActionListener(e -> moveSelectedUp());
        deleteButton.addActionListener(e -> deleteSelected());
        clearLogButton.addActionListener(e -> clearResponseDisplay());

        controlPanel.add(new JLabel("延迟(ms):"));
        controlPanel.add(delayField);
        controlPanel.add(new JLabel("并发次数:"));
        controlPanel.add(concurrentCountField);
        controlPanel.add(sendButton);
        controlPanel.add(singleConcurrentButton);
        controlPanel.add(stopButton);
        controlPanel.add(clearButton);
        controlPanel.add(clearLogButton);
        controlPanel.add(moveUpButton);
        controlPanel.add(deleteButton);

        // 构造表格（序号、方法、URL）
        String[] columnNames = {"序号", "方法", "URL"};
        tableModel = new DefaultTableModel(columnNames, 0);
        requestTable = new JTable(tableModel);
        // 修改为多选模式，支持Ctrl键多选
        requestTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // 选中某一行时，将对应请求内容显示在可编辑的请求框中
        requestTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int[] selectedRows = requestTable.getSelectedRows();
                if (selectedRows.length == 1) {
                    // 单选时显示请求内容
                    int index = selectedRows[0];
                    if (index != -1 && index < rawRequests.size()) {
                        requestDisplayArea.setText(formatRequest(rawRequests.get(index)));
                    }
                } else if (selectedRows.length > 1) {
                    // 多选时显示第一个选中请求的内容
                    int firstIndex = selectedRows[0];
                    if (firstIndex != -1 && firstIndex < rawRequests.size()) {
                        requestDisplayArea.setText(formatRequest(rawRequests.get(firstIndex)));
                        // 在响应日志框中显示选中数量
                        responseDisplayArea.setText(String.format("[系统] 已选择 %d 个请求\n", selectedRows.length));
                    }
                } else {
                    requestDisplayArea.setText("");
                }
            }
        });

        // 设置列宽
        TableColumnModel columnModel = requestTable.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(80);
        columnModel.getColumn(0).setMinWidth(50);
        columnModel.getColumn(0).setMaxWidth(100);
        columnModel.getColumn(1).setPreferredWidth(80);
        columnModel.getColumn(1).setMinWidth(50);
        columnModel.getColumn(1).setMaxWidth(100);

        JScrollPane tableScrollPane = new JScrollPane(requestTable);

        // 请求框和响应框
        // 请求框设置为可编辑，允许用户直接修改请求内容
        requestDisplayArea = new JTextArea();
        requestDisplayArea.setEditable(true);
        requestDisplayArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        // 添加文档监听器，实时更新当前请求内容
        requestDisplayArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCurrentRequest();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCurrentRequest();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCurrentRequest();
            }
        });
        // 响应框保持只读
        responseDisplayArea = createTextArea();

        JScrollPane requestScrollPane = new JScrollPane(requestDisplayArea);
        JScrollPane responseScrollPane = new JScrollPane(responseDisplayArea);

        // 创建一个面板来包含URL表格和请求/响应区域
        JPanel contentPanel = new JPanel(new BorderLayout());
        
        // 创建分割面板来分隔URL表格和请求/响应区域
        JSplitPane contentSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        contentSplitPane.setTopComponent(tableScrollPane);  // URL表格
        
        // 创建请求和响应的水平分割面板
        JSplitPane requestResponsePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        requestResponsePane.setLeftComponent(requestScrollPane);
        requestResponsePane.setRightComponent(responseScrollPane);
        requestResponsePane.setResizeWeight(0.5);  // 请求和响应区域1:1分割
        
        contentSplitPane.setBottomComponent(requestResponsePane);
        contentSplitPane.setResizeWeight(0.2);  // URL表格占20%，请求/响应区域占80%
        
        // 将控制面板放在顶部，内容面板放在中间
        panel = new JPanel(new BorderLayout());
        panel.add(controlPanel, BorderLayout.NORTH);  // 控制按钮面板保持原始高度
        panel.add(contentSplitPane, BorderLayout.CENTER);  // 内容区域填充剩余空间

        requestDisplayArea.setLineWrap(true);
        requestDisplayArea.setWrapStyleWord(true);

    }

    private JTextArea createTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        area.setLineWrap(true); // 启用自动换行
        area.setWrapStyleWord(true); // 按单词换行，避免字符被截断
        return area;
    }


    // 向上移动选中的请求
    private void moveSelectedUp() {
        int index = requestTable.getSelectedRow();
        if (index > 0) {
            tableModel.moveRow(index, index, index - 1);
            rawRequests.add(index - 1, rawRequests.remove(index));
            requestTable.setRowSelectionInterval(index - 1, index - 1);
            updateSequenceNumbers();
        }
    }

    // 删除选中的请求
    private void deleteSelected() {
        int index = requestTable.getSelectedRow();
        if (index != -1) {
            tableModel.removeRow(index);
            rawRequests.remove(index);
            requestDisplayArea.setText("");
            updateSequenceNumbers();
        }
    }

    private void updateSequenceNumbers() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt("请求-" + (i + 1), i, 0);
        }
    }

    /**
     * 添加请求时，同时解析 HTTP 方法和 URL 并更新表格。
     */
    public void addRequest(byte[] request) {
        if (helpers != null && baseService != null) {
            try {
                IRequestInfo requestInfo = helpers.analyzeRequest(baseService, request);
                String method = requestInfo.getMethod();
                String fullUrl = requestInfo.getUrl().toString();
                tableModel.addRow(new Object[]{"请求-" + (tableModel.getRowCount() + 1), method, fullUrl});
            } catch (Exception e) {
                tableModel.addRow(new Object[]{"请求-" + (tableModel.getRowCount() + 1), "UNKNOWN", "unknown"});
            }
        } else {
            tableModel.addRow(new Object[]{"请求-" + (tableModel.getRowCount() + 1), "UNKNOWN", "unknown"});
        }
        rawRequests.add(request);
    }

    public JPanel getPanel() {
        return panel;
    }

    public String getDelay() {
        return delayField.getText();
    }

    public List<byte[]> getRequestList() {
        return rawRequests;
    }

    public IHttpService getBaseService() {
        return baseService;
    }

    public JButton getSendButton() {
        return sendButton;
    }

    public JButton getStopButton() {
        return stopButton;
    }

    public JButton getClearButton() {
        return clearButton;
    }

    public void setBaseService(IHttpService service) {
        this.baseService = service;
    }

    public void clearResponseDisplay() {
        responseDisplayArea.setText("");
    }

    public void appendResponse(String text) {
        responseDisplayArea.append(text);
        responseDisplayArea.setCaretPosition(responseDisplayArea.getDocument().getLength());
    }

    // 清空表格和请求列表
    public void clearAll() {
        tableModel.setRowCount(0);
        rawRequests.clear();
        requestDisplayArea.setText("");
        responseDisplayArea.setText("");
    }

    // 根据攻击状态切换按钮启用状态
    public void toggleButtons(boolean enable) {
        SwingUtilities.invokeLater(() -> {
            sendButton.setEnabled(enable);
            stopButton.setEnabled(!enable);
        });
    }

    /**
     * 格式化请求内容：如果请求体为 JSON，则自动进行缩进格式化。
     */
    private String formatRequest(byte[] request) {
        if (helpers != null && baseService != null) {
            try {
                IRequestInfo requestInfo = helpers.analyzeRequest(baseService, request);
                String requestText = new String(request, StandardCharsets.UTF_8);
                
                // 获取完整的请求内容，包括起始行
                List<String> headers = requestInfo.getHeaders();
                StringBuilder fullRequest = new StringBuilder();
                
                // 添加所有请求头
                for (String header : headers) {
                    fullRequest.append(header).append("\r\n");
                }
                
                // 添加空行和请求体
                fullRequest.append("\r\n");
                if (requestInfo.getBodyOffset() < request.length) {
                    String body = requestText.substring(requestInfo.getBodyOffset());
                    
                    // 检查Content-Type是否为JSON
                    boolean isJson = false;
                    for (String header : headers) {
                        if (header.toLowerCase().contains("content-type")
                                && header.toLowerCase().contains("application/json")) {
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
                            // JSON格式化失败，保持原样
                        }
                    }
                    fullRequest.append(body);
                }
                
                return fullRequest.toString();
            } catch (Exception e) {
                // 如果解析失败，返回原始内容
                return new String(request, StandardCharsets.UTF_8);
            }
        } else {
            return new String(request, StandardCharsets.UTF_8);
        }
    }

    /**
     * 当用户在请求框中直接修改请求内容时，实时更新 rawRequests 中对应的数据，
     * 并调用 updateRequestContent 更新 Content-Length 头。
     */
    private void updateCurrentRequest() {
        int index = requestTable.getSelectedRow();
        if (index != -1 && index < rawRequests.size()) {
            rawRequests.set(index, updateRequestContent(requestDisplayArea.getText()));
        }
    }

    /**
     * 将请求文本中的请求头和请求体分离，重新计算请求体字节数，
     * 更新或添加 Content-Length 头后返回新的请求字节数组。
     */
    private byte[] updateRequestContent(String requestText) {
        // 假设请求头与请求体之间以 "\r\n\r\n" 分隔
        int headerEndIndex = requestText.indexOf("\r\n\r\n");
        if (headerEndIndex != -1) {
            String headers = requestText.substring(0, headerEndIndex);
            String body = requestText.substring(headerEndIndex + 4);
            int newContentLength = body.getBytes(StandardCharsets.UTF_8).length;
            String[] headerLines = headers.split("\r\n");
            StringBuilder newHeaders = new StringBuilder();
            boolean found = false;
            for (String line : headerLines) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    newHeaders.append("Content-Length: ").append(newContentLength).append("\r\n");
                    found = true;
                } else {
                    newHeaders.append(line).append("\r\n");
                }
            }
            if (!found) {
                // 如果原请求中没有 Content-Length 头，则添加
                newHeaders.append("Content-Length: ").append(newContentLength).append("\r\n");
            }
            String newRequestText = newHeaders.toString() + "\r\n" + body;
            return newRequestText.getBytes(StandardCharsets.UTF_8);
        } else {
            return requestText.getBytes(StandardCharsets.UTF_8);
        }
    }

    private boolean isValidDelay(String delay) {
        try {
            double value = Double.parseDouble(delay);
            return value >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public JButton getSingleConcurrentButton() {
        return singleConcurrentButton;
    }

    public String getConcurrentCount() {
        return concurrentCountField.getText();
    }

    // 修改获取选中请求的方法，支持多选
    public List<byte[]> getSelectedRequests() {
        List<byte[]> selectedRequests = new ArrayList<>();
        int[] selectedRows = requestTable.getSelectedRows();
        for (int row : selectedRows) {
            if (row >= 0 && row < rawRequests.size()) {
                selectedRequests.add(rawRequests.get(row));
            }
        }
        return selectedRequests;
    }

    // 获取选中的请求数量
    public int getSelectedRequestCount() {
        return requestTable.getSelectedRows().length;
    }

    // 验证并发次数是否有效
    public boolean isValidConcurrentCount() {
        try {
            String count = concurrentCountField.getText();
            if (count.trim().isEmpty() || count.equals("0")) {
                return true; // 空值或0表示无限次
            }
            int value = Integer.parseInt(count);
            return value > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public JButton getClearLogButton() {
        return clearLogButton;
    }
}
