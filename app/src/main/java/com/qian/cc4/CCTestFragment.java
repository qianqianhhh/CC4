package com.qian.cc4;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CCTestFragment extends BaseFragment {

    private EditText etTargetUrl, etThreadCount, etRequestCount, etDelay, etPostData;
    private Button btnStart, btnStop, btnClearLog, btnSaveConfig, btnLoadConfig;
    private TextView tvLog, tvSuccessCount, tvFailCount, tvTotalCount, tvAvgResponseTime;
    private Spinner spinnerUserAgent, spinnerContentType;
    private CheckBox cbKeepScreenOn, cbRandomDelay, cbFollowRedirects, cbRandomUA;
    private ProgressBar progressBar;
    private RadioGroup rgRequestMethod;
    private RadioButton rbGet, rbPost;
    private LinearLayout layoutPostData;
    private AtomicInteger successCount = new AtomicInteger(0);
    private AtomicInteger failCount = new AtomicInteger(0);
    private AtomicInteger totalCount = new AtomicInteger(0);
    private long totalResponseTime = 0;
    private volatile boolean isTesting = false;
    private ExecutorService executorService;
    private Handler mainHandler;

    private SharedPreferences preferences;

    private final List<String> userAgentList = Arrays.asList(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Safari/605.1.15",
        "Mozilla/5.0 (Linux; Android 10; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.162 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1"
    );
    
    private final String[] uaNames = {"Chrome Windows", "Firefox Windows", "Safari Mac", "Chrome Android", "iPhone Safari", "自定义UA"};

    private final List<String> contentTypeList = Arrays.asList(
        "application/x-www-form-urlencoded",
        "application/json",
        "text/plain",
        "multipart/form-data"
    );
    
    private final String[] contentTypeNames = {"表单数据", "JSON", "纯文本"};
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cc_test, container, false);
        initViews(view);
        setupUserAgentSpinner();
        setupContentTypeSpinner();
        setupRequestMethodListener();
        mainHandler = new Handler(Looper.getMainLooper());
        
        preferences = requireActivity().getSharedPreferences("cc_config", getContext().MODE_PRIVATE);
        loadConfig();
        
        return view;
    }
    
    private void initViews(View view) {
        // 初始化所有UI控件
        etTargetUrl = view.findViewById(R.id.etTargetUrl);
        etThreadCount = view.findViewById(R.id.etThreadCount);
        etRequestCount = view.findViewById(R.id.etRequestCount);
        etDelay = view.findViewById(R.id.etDelay);
        etPostData = view.findViewById(R.id.etPostData);
        
        btnStart = view.findViewById(R.id.btnStart);
        btnStop = view.findViewById(R.id.btnStop);
        btnClearLog = view.findViewById(R.id.btnClearLog);
        btnSaveConfig = view.findViewById(R.id.btnSaveConfig);
        btnLoadConfig = view.findViewById(R.id.btnLoadConfig);
        
        tvLog = view.findViewById(R.id.tvLog);
        tvSuccessCount = view.findViewById(R.id.tvSuccessCount);
        tvFailCount = view.findViewById(R.id.tvFailCount);
        tvTotalCount = view.findViewById(R.id.tvTotalCount);
        tvAvgResponseTime = view.findViewById(R.id.tvAvgResponseTime);
        
        spinnerUserAgent = view.findViewById(R.id.spinnerUserAgent);
        spinnerContentType = view.findViewById(R.id.spinnerContentType);
        cbKeepScreenOn = view.findViewById(R.id.cbKeepScreenOn);
        cbRandomDelay = view.findViewById(R.id.cbRandomDelay);
        cbFollowRedirects = view.findViewById(R.id.cbFollowRedirects);
        cbRandomUA = view.findViewById(R.id.cbRandomUA);
        progressBar = view.findViewById(R.id.progressBar);
        rgRequestMethod = view.findViewById(R.id.rgRequestMethod);
        rbGet = view.findViewById(R.id.rbGet);
        rbPost = view.findViewById(R.id.rbPost);
        layoutPostData = view.findViewById(R.id.layoutPostData);
        btnStart.setOnClickListener(v -> startTest());
        btnStop.setOnClickListener(v -> stopTest());
        btnClearLog.setOnClickListener(v -> clearLog());
        btnSaveConfig.setOnClickListener(v -> saveConfiguration());
        btnLoadConfig.setOnClickListener(v -> loadConfiguration());

        cbKeepScreenOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getActivity() != null) {
                if (isChecked) {
                    getActivity().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        });
    }
    
    private void setupUserAgentSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, uaNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUserAgent.setAdapter(adapter);
        spinnerUserAgent.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                if ("自定义UA".equals(selected)) {
                    showCustomUADialog();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    
    private void setupContentTypeSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, contentTypeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerContentType.setAdapter(adapter);
    }
    
    private void setupRequestMethodListener() {
        rgRequestMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbPost) {
                layoutPostData.setVisibility(View.VISIBLE);
            } else {
                layoutPostData.setVisibility(View.GONE);
            }
        });
    }
    
    private void showCustomUADialog() {
        EditText input = new EditText(getContext());
        input.setHint("输入自定义User-Agent");
        
        new android.app.AlertDialog.Builder(getContext())
            .setTitle("自定义User-Agent")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                String customUA = input.getText().toString().trim();
                if (!customUA.isEmpty()) {
                    showToast("自定义UA已设置");
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void startTest() {
        String url = etTargetUrl.getText().toString().trim();
        if (url.isEmpty()) {
            showToast("请输入目标URL");
            return;
        }
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
            etTargetUrl.setText(url);
        }

        boolean isPost = rbPost.isChecked();
        String postData = etPostData.getText().toString().trim();
        String contentType = contentTypeList.get(spinnerContentType.getSelectedItemPosition());
        
        if (isPost && postData.isEmpty()) {
            showToast("POST方法需要输入POST数据");
            return;
        }
        
        try {
            int threadCount = Integer.parseInt(etThreadCount.getText().toString());
            int requestCount = Integer.parseInt(etRequestCount.getText().toString());
            int delay = Integer.parseInt(etDelay.getText().toString());
            
            if (threadCount <= 0 || requestCount <= 0) {
                showToast("线程数和请求数必须大于0");
                return;
            }

            resetCounters();
            
            isTesting = true;
            updateButtonStates();
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setMax(requestCount);
            
            executorService = Executors.newFixedThreadPool(threadCount);
            
            logMessage("开始压力测试...");
            logMessage("目标URL: " + url);
            logMessage("请求方法: " + (isPost ? "POST" : "GET"));
            logMessage("线程数: " + threadCount + ", 总请求数: " + requestCount);
            if (isPost) {
                logMessage("Content-Type: " + contentType);
            }

            final boolean followRedirects = cbFollowRedirects.isChecked();
            final boolean randomUA = cbRandomUA.isChecked();
            final String finalUrl = url;
            final int finalDelay = delay;
            final int finalRequestCount = requestCount;
            final boolean finalIsPost = isPost;
            final String finalPostData = postData;
            final String finalContentType = contentType;
            
            for (int i = 0; i < requestCount; i++) {
                if (!isTesting) break;
                
                final int requestIndex = i;
                
                executorService.execute(() -> {
                    if (isTesting) {
                        long startTime = System.currentTimeMillis();

                        String userAgent;
                        if (randomUA) {
                            userAgent = userAgentList.get(new Random().nextInt(userAgentList.size()));
                        } else {
                            int position = spinnerUserAgent.getSelectedItemPosition();
                            userAgent = position >= 0 && position < userAgentList.size() ? userAgentList.get(position) : "";
                        }
                        
                        boolean success = sendRequest(finalUrl, requestIndex, userAgent, followRedirects, 
                                                     finalIsPost, finalPostData, finalContentType);
                        long responseTime = System.currentTimeMillis() - startTime;
                        
                        if (success) {
                            totalResponseTime += responseTime;
                        }

                        mainHandler.post(() -> progressBar.setProgress(totalCount.get()));

                        if (successCount.get() > 0) {
                            long avgTime = totalResponseTime / successCount.get();
                            mainHandler.post(() -> tvAvgResponseTime.setText(avgTime + "ms"));
                        }
                    }

                    if (finalDelay > 0) {
                        try {
                            int actualDelay = cbRandomDelay.isChecked() ?
                                (int) (finalDelay * (0.5 + Math.random())) : finalDelay;
                            Thread.sleep(actualDelay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }
            
        } catch (NumberFormatException e) {
            showToast("请输入有效的数字参数");
        }
    }
    
    private boolean sendRequest(String url, int requestIndex, String userAgent, boolean followRedirects,
                               boolean isPost, String postData, String contentType) {
        HttpURLConnection connection = null;
        try {
            URL targetUrl = new URL(url);
            connection = (HttpURLConnection) targetUrl.openConnection();

            if (isPost) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
            } else {
                connection.setRequestMethod("GET");
            }

            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(followRedirects);

            if (userAgent != null && !userAgent.isEmpty()) {
                connection.setRequestProperty("User-Agent", userAgent);
            }
            
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

            if (isPost && !TextUtils.isEmpty(postData)) {
                if (!TextUtils.isEmpty(contentType)) {
                    connection.setRequestProperty("Content-Type", contentType);
                }
                
                byte[] postDataBytes = postData.getBytes("UTF-8");
                connection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

                try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                    outputStream.write(postDataBytes);
                    outputStream.flush();
                }
            }
            
            int responseCode = connection.getResponseCode();
            totalCount.incrementAndGet();
            
            if (responseCode >= 200 && responseCode < 400) {
                successCount.incrementAndGet();
                logMessage("请求 " + (requestIndex + 1) + ": " + (isPost ? "POST" : "GET") + " 成功 (状态码: " + responseCode + ")");
                return true;
            } else {
                failCount.incrementAndGet();
                logMessage("请求 " + (requestIndex + 1) + ": " + (isPost ? "POST" : "GET") + " 失败 (状态码: " + responseCode + ")");
                return false;
            }
            
        } catch (IOException e) {
            failCount.incrementAndGet();
            totalCount.incrementAndGet();
            logMessage("请求 " + (requestIndex + 1) + ": " + (isPost ? "POST" : "GET") + " 异常 - " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            updateCounters();
        }
    }
    
    private void stopTest() {
        isTesting = false;
        if (executorService != null) {
            executorService.shutdownNow();
        }
        
        updateButtonStates();
        progressBar.setVisibility(View.GONE);
        logMessage("测试已停止");
    }
    
    private void clearLog() {
        tvLog.setText("日志已清空\n");
        resetCounters();
    }
    
    private void saveConfiguration() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("targetUrl", etTargetUrl.getText().toString());
        editor.putString("threadCount", etThreadCount.getText().toString());
        editor.putString("requestCount", etRequestCount.getText().toString());
        editor.putString("delay", etDelay.getText().toString());
        editor.putInt("userAgent", spinnerUserAgent.getSelectedItemPosition());
        editor.putBoolean("keepScreenOn", cbKeepScreenOn.isChecked());
        editor.putBoolean("randomDelay", cbRandomDelay.isChecked());
        editor.putBoolean("followRedirects", cbFollowRedirects.isChecked());
        editor.putBoolean("randomUA", cbRandomUA.isChecked());
        editor.putBoolean("isPost", rbPost.isChecked());
        editor.putString("postData", etPostData.getText().toString());
        editor.putInt("contentType", spinnerContentType.getSelectedItemPosition());
        
        editor.apply();
        showToast("配置已保存");
    }
    
    private void loadConfiguration() {
        etTargetUrl.setText(preferences.getString("targetUrl", ""));
        etThreadCount.setText(preferences.getString("threadCount", "5"));
        etRequestCount.setText(preferences.getString("requestCount", "50"));
        etDelay.setText(preferences.getString("delay", "200"));
        spinnerUserAgent.setSelection(preferences.getInt("userAgent", 0));
        cbKeepScreenOn.setChecked(preferences.getBoolean("keepScreenOn", false));
        cbRandomDelay.setChecked(preferences.getBoolean("randomDelay", false));
        cbFollowRedirects.setChecked(preferences.getBoolean("followRedirects", true));
        cbRandomUA.setChecked(preferences.getBoolean("randomUA", false));

        boolean isPost = preferences.getBoolean("isPost", false);
        if (isPost) {
            rbPost.setChecked(true);
            layoutPostData.setVisibility(View.VISIBLE);
        } else {
            rbGet.setChecked(true);
            layoutPostData.setVisibility(View.GONE);
        }
        etPostData.setText(preferences.getString("postData", ""));
        spinnerContentType.setSelection(preferences.getInt("contentType", 0));
        
        showToast("配置已加载");
    }
    
    private void loadConfig() {
        loadConfiguration();
    }
    
    private void resetCounters() {
        successCount.set(0);
        failCount.set(0);
        totalCount.set(0);
        totalResponseTime = 0;
        updateCounters();
        tvAvgResponseTime.setText("0ms");
    }
    
    private void updateButtonStates() {
        btnStart.setEnabled(!isTesting);
        btnStop.setEnabled(isTesting);
    }
    
    private void updateCounters() {
        mainHandler.post(() -> {
            tvSuccessCount.setText(String.valueOf(successCount.get()));
            tvFailCount.setText(String.valueOf(failCount.get()));
            tvTotalCount.setText(String.valueOf(totalCount.get()));
        });
    }
    
    private void logMessage(final String message) {
        mainHandler.post(() -> {
            String currentText = tvLog.getText().toString();
            String newText = new Date().toString().substring(11, 19) + " - " + message + "\n" + currentText;
            if (newText.length() > 5000) {
                newText = newText.substring(0, 5000) + "\n日志过长，已截断...";
            }
            tvLog.setText(newText);
        });
    }
    
    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTest();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
