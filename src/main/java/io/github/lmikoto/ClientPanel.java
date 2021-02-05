package io.github.lmikoto;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import lombok.Getter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author liuyang
 * 2021/2/4 5:46 下午
 */
public class ClientPanel extends JPanel {

    private JPanel mainPanel;

    private JButton runBtn;

    @Getter
    private JTextField interfaceName;

    private JPanel reqPane;

    private JPanel respPane;

    private JButton saveBtn;

    private JButton delBtn;

    private JComboBox<String> addressBox;

    @Getter
    private JTextField methodName;

    private JTextField version;
    private JLabel tips;
    private JTextField timeout;

    @Getter
    private JsonEditor jsonEditorReq;

    private JsonEditor jsonEditorResp;

    @Getter
    private Project project;

    private DubboEntity entity;


    public ClientPanel(Project project, ToolWindow toolWindow){
        initUI(project);
        initListener();
    }

    private void initUI(Project project) {
        entity = new DubboEntity();
        this.project = project;
        setLayout(new BorderLayout());
        add(mainPanel,BorderLayout.CENTER,0);
        jsonEditorReq = new JsonEditor(project);
        jsonEditorResp = new JsonEditor(project);

        reqPane.add(jsonEditorReq,BorderLayout.CENTER,0);
        respPane.add(jsonEditorResp,BorderLayout.CENTER,0);

        Setting setting = Setting.getInstance();
        for (String address: setting.getAddress()){
            addressBox.addItem(address);
        }
    }

    private void initListener() {
        saveBtn.addActionListener((e) -> {
            String selectedItem = (String)addressBox.getSelectedItem();
            Setting.getInstance().getAddress().add(selectedItem);
            addressBox.addItem(selectedItem);
        });

        delBtn.addActionListener((e) -> {
            String selectedItem = (String)this.addressBox.getSelectedItem();
            Setting.getInstance().getAddress().remove(selectedItem);
            this.addressBox.removeItem(selectedItem);
        });

        runBtn.addActionListener((e) -> {

            refreshEntity();

            // 清空返回
            writeDocument(project, jsonEditorResp.getDocument(), "");

            // 开一个线程去跑防止ui卡死
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            final Future<Object> submit = executorService.submit(() -> {
                try {
                    tips.setText("正在请求...");
                    tips.updateUI();
                    long start = System.currentTimeMillis();
                    Object result = DubboUtils.invoke(entity);
                    writeDocument(project, this.jsonEditorResp.getDocument(), JsonUtils.toPrettyJson(result));
                    long end = System.currentTimeMillis();
                    tips.setText("耗时:" + (end - start) + "ms");
                    tips.updateUI();
                    return result;
                } catch (Exception ex) {
                    tips.setText("错误:" + ex.getMessage());
                    tips.updateUI();
                    return new Object();
                }
            });
        });
    }

    private void refreshEntity() {
        JsonEditor jsonEditorReq = this.getJsonEditorReq();
        entity.setMethodName(methodName.getText());
        entity.setInterfaceName(interfaceName.getText());
        entity.setAddress((String)addressBox.getSelectedItem());
        entity.setVersion(version.getText());
        entity.setTimeout(StringUtils.isBlank(timeout.getText()) ? null : Integer.valueOf(timeout.getText()));
        if (jsonEditorReq.getDocumentText() != null && jsonEditorReq.getDocumentText().length() > 0) {
            Map<String,Object> map = JsonUtils.fromJson(jsonEditorReq.getDocumentText(),Map.class);
            List<String> methodTypeList = (List<String>)map.get(Const.METHOD_TYPE);
            if (CollectionUtils.isNotEmpty(methodTypeList)) {
                entity.setMethodType(methodTypeList.toArray(new String[0]));
            } else {
                entity.setMethodType(new String[0]);
            }

            List<Object> paramList = (List<Object>) map.get(Const.PARAM);
            if (CollectionUtils.isNotEmpty(paramList)) {
                entity.setParam(paramList.toArray());
            } else {
                entity.setParam(new Object[0]);
            }
        } else {
            entity.setParam(new Object[0]);
            entity.setMethodType(new String[0]);
        }
    }

    public static void refreshUI(ClientPanel client, DubboEntity entity) {
        JTextField textField1 = client.getInterfaceName();
        JTextField textField2 = client.getMethodName();
        JsonEditor jsonEditorReq = client.getJsonEditorReq();
        textField1.setText(entity.getInterfaceName());
        textField2.setText(entity.getMethodName());
        Map<String, Object> map = new HashMap();
        map.put(Const.PARAM, entity.getParam());
        map.put(Const.METHOD_TYPE, entity.getMethodType());
        writeDocument(client.getProject(), jsonEditorReq.getDocument(), JsonUtils.toPrettyJson(map));
        client.updateUI();
    }

    private static void writeDocument(Project project, Document document, String text) {
        WriteCommandAction.runWriteCommandAction(project, () -> document.setText(text));
    }

}
