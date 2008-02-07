/*
 * Christopher Deckers (chrriis@nextencia.net)
 * http://www.nextencia.net
 * 
 * See the file "readme.txt" for information on usage and redistribution of
 * this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */
package chrriis.dj.nativeswing.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;

import chrriis.common.Registry;
import chrriis.common.Utils;
import chrriis.common.WebServer;
import chrriis.common.WebServer.WebServerContent;
import chrriis.dj.nativeswing.Disposable;
import chrriis.dj.nativeswing.NativeInterfaceHandler;
import chrriis.dj.nativeswing.ui.event.FlashPlayerListener;
import chrriis.dj.nativeswing.ui.event.FlashPlayerWindowOpeningEvent;
import chrriis.dj.nativeswing.ui.event.WebBrowserAdapter;
import chrriis.dj.nativeswing.ui.event.WebBrowserEvent;
import chrriis.dj.nativeswing.ui.event.WebBrowserWindowOpeningEvent;

/**
 * @author Christopher Deckers
 */
public class JFlashPlayer extends JPanel implements Disposable {

  public static class FlashLoadingOptions {
    
    public FlashLoadingOptions() {
      this(null, null);
    }
    
    public FlashLoadingOptions(Map<String, String> parameters, Map<String, String> variables) {
      setParameters(parameters);
      setVariables(variables);
    }
    
    protected Map<String, String> keyToValueVariableMap = new HashMap<String, String>();
    
    public Map<String, String> getVariables() {
      return keyToValueVariableMap;
    }
    
    public void setVariables(Map<String, String> keyToValueVariableMap) {
      if(keyToValueVariableMap == null) {
        keyToValueVariableMap = new HashMap<String, String>();
      }
      this.keyToValueVariableMap = keyToValueVariableMap;
    }
    
    protected Map<String, String> keyToValueParameterMap = new HashMap<String, String>();
    
    public Map<String, String> getParameters() {
      return keyToValueParameterMap;
    }
    
    public void setParameters(Map<String, String> keyToValueParameterMap) {
      if(keyToValueParameterMap == null) {
        keyToValueParameterMap = new HashMap<String, String>();
      }
      this.keyToValueParameterMap = keyToValueParameterMap;
    }
    
  }
  
  private final ResourceBundle RESOURCES = ResourceBundle.getBundle(JFlashPlayer.class.getPackage().getName().replace('.', '/') + "/resource/FlashPlayer");

  private JPanel webBrowserPanel;
  private JWebBrowser webBrowser;
  
  private JPanel controlBarPane;
  private JButton playButton;
  private JButton pauseButton;
  private JButton stopButton;

  private static class NWebBrowserListener extends WebBrowserAdapter {
    protected Reference<JFlashPlayer> flashPlayer;
    protected NWebBrowserListener(JFlashPlayer flashPlayer) {
      this.flashPlayer = new WeakReference<JFlashPlayer>(flashPlayer);
    }
//    @Override
//    public void urlChanging(WebBrowserNavigationEvent e) {
//      if(url == null || !url.equals(e.getNewURL())) {
//        e.consume();
//      }
//    }
    @Override
    public void windowOpening(WebBrowserWindowOpeningEvent ev) {
      JFlashPlayer flashPlayer = this.flashPlayer.get();
      if(flashPlayer == null) {
        return;
      }
      Object[] listeners = flashPlayer.listenerList.getListenerList();
      FlashPlayerWindowOpeningEvent e = null;
      for(int i=listeners.length-2; i>=0 && !ev.isConsumed(); i-=2) {
        if(listeners[i] == FlashPlayerListener.class) {
          if(e == null) {
            e = new FlashPlayerWindowOpeningEvent(flashPlayer, ev.getNewWebBrowser(), ev.getNewURL(), ev.getLocation(), ev.getSize());
          }
          ((FlashPlayerListener)listeners[i + 1]).windowOpening(e);
          if(e.isConsumed()) {
            ev.consume();
          } else {
            ev.setNewWebBrowser(e.getNewWebBrowser());
          }
        }
      }
    }
  }
  
  private int instanceID;
  
  public JFlashPlayer() {
    super(new BorderLayout(0, 0));
    webBrowserPanel = new JPanel(new BorderLayout(0, 0));
    webBrowser = new JWebBrowser();
    webBrowser.setBarsVisible(false);
    webBrowser.addWebBrowserListener(new NWebBrowserListener(this));
    webBrowserPanel.add(webBrowser, BorderLayout.CENTER);
    add(webBrowserPanel, BorderLayout.CENTER);
    controlBarPane = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
    playButton = new JButton(createIcon("PlayIcon"));
    playButton.setToolTipText(RESOURCES.getString("PlayText"));
    playButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        play();
      }
    });
    controlBarPane.add(playButton);
    pauseButton = new JButton(createIcon("PauseIcon"));
    pauseButton.setToolTipText(RESOURCES.getString("PauseText"));
    pauseButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        pause();
      }
    });
    controlBarPane.add(pauseButton);
    stopButton = new JButton(createIcon("StopIcon"));
    stopButton.setToolTipText(RESOURCES.getString("StopText"));
    stopButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        stop();
      }
    });
    controlBarPane.add(stopButton);
    add(controlBarPane, BorderLayout.SOUTH);
    adjustBorder();
    instanceID = Registry.getInstance().add(this);
  }
  
  private void adjustBorder() {
    if(isControlBarVisible()) {
      webBrowserPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
    } else {
      webBrowserPanel.setBorder(null);
    }
  }
  
  private Icon createIcon(String resourceKey) {
    String value = RESOURCES.getString(resourceKey);
    return value.length() == 0? null: new ImageIcon(JWebBrowser.class.getResource(value));
  }
  
  private String url;
  
  public String getURL() {
    return url;
  }
  
  public void setURL(String url) {
    setURL(url, new FlashLoadingOptions());
  }
  
  private FlashLoadingOptions loadingOptions;
  
  @SuppressWarnings("deprecation")
  public void setURL(String url, FlashLoadingOptions loadingOptions) {
    this.loadingOptions = loadingOptions;
    if(url == null) {
      webBrowser.setText("");
      this.url = url;
      return;
    }
    try {
      new URL(url);
    } catch(Exception e) {
      url = new File(url).toURI().toString();
    }
    this.url = url;
    url = WebServer.getDefaultWebServer().getDynamicContentURL(JFlashPlayer.class.getName(), "html/" + instanceID);
    webBrowser.setURL(url);
  }

  public void play() {
    if(url == null) {
      return;
    }
    webBrowser.execute("playFM();");
  }
  
  public void pause() {
    if(url == null) {
      return;
    }
    webBrowser.execute("stopFM();");
  }
  
  public void stop() {
    if(url == null) {
      return;
    }
    webBrowser.execute("rewindFM();");
  }
  
  public void setVariable(String name, String value) {
    if(url == null) {
      return;
    }
    webBrowser.execute("setVariableFM('" + Utils.encodeURL(name) + "', '" + Utils.encodeURL(value) + "')");
  }
  
  /**
   * @return The value, or null or an empty string when the variable is not defined.
   */
  public String getVariable(String name) {
    if(url == null) {
      return null;
    }
    final String TEMP_RESULT = new String();
    final String[] getVariableResult = new String[] {TEMP_RESULT};
    webBrowser.addWebBrowserListener(new WebBrowserAdapter() {
      @Override
      public void commandReceived(WebBrowserEvent e, String command) {
        if(command.startsWith("getVariableFM:")) {
          getVariableResult[0] = command.substring("getVariableFM:".length());
          webBrowser.removeWebBrowserListener(this);
        }
      }
    });
    webBrowser.execute("getVariableFM('" + Utils.encodeURL(name) + "');");
    for(int i=0; i<20; i++) {
      if(getVariableResult[0] != TEMP_RESULT) {
        break;
      }
      NativeInterfaceHandler.invokeSWT(new Runnable() {
        public void run() {
          if(getVariableResult[0] != TEMP_RESULT) {
            return;
          }
          try {
            Thread.sleep(50);
          } catch(Exception e) {
          }
        }
      });
    }
    String result = getVariableResult[0];
    return result == TEMP_RESULT? null: result;
  }
  
  public boolean isControlBarVisible() {
    return controlBarPane.isVisible();
  }
  
  public void setControlBarVisible(boolean isVisible) {
    controlBarPane.setVisible(isVisible);
    adjustBorder();
  }
  
  public void addFlashPlayerListener(FlashPlayerListener listener) {
    listenerList.add(FlashPlayerListener.class, listener);
  }
  
  public void removeFlashPlayerListener(FlashPlayerListener listener) {
    listenerList.remove(FlashPlayerListener.class, listener);
  }
  
  public FlashPlayerListener[] getFlashPlayerListeners() {
    return listenerList.getListeners(FlashPlayerListener.class);
  }
  
  private static final String LS = System.getProperty("line.separator");

  protected static WebServerContent getWebServerContent(String resourcePath) {
    int index = resourcePath.indexOf('/');
    String type = resourcePath.substring(0, index);
    resourcePath = resourcePath.substring(index + 1);
    if("html".equals(type)) {
      final int instanceID = Integer.parseInt(resourcePath);
      JFlashPlayer player = (JFlashPlayer)Registry.getInstance().get(instanceID);
      if(player == null) {
        return null;
      }
      return new WebServerContent() {
        @Override
        public String getContentType() {
          return getDefaultMimeType(".html");
        }
        @Override
        public InputStream getInputStream() {
          try {
            String content =
                "<html>" + LS +
                "  <head>" + LS +
                "    <script language=\"JavaScript\" type=\"text/javascript\">" + LS +
                "      <!--" + LS +
                "      function sendCommand(command) {" + LS +
                "        command = command == null? '': encodeURIComponent(command);" + LS +
                "        window.location = 'command://' + command;" + LS +
                "      }" + LS +
                "      function getFlashMovieObject() {" + LS +
                "        var movieName = \"myFlashMovie\";" + LS +
                "        if(window.document[movieName]) {" + LS +
                "          return window.document[movieName];" + LS +
                "        }" + LS +
                "        if(navigator.appName.indexOf(\"Microsoft Internet\") == -1) {" + LS +
                "          if(document.embeds && document.embeds[movieName]) {" + LS +
                "            return document.embeds[movieName];" + LS +
                "          }" + LS +
                "        } else {" + LS +
                "          return document.getElementById(movieName);" + LS +
                "        }" + LS +
                "      }" + LS +
                "      function playFM() {" + LS +
                "        var flashMovie = getFlashMovieObject();" + LS +
                "        flashMovie.Play();" + LS +
                "      }" + LS +
                "      function stopFM() {" + LS +
                "        var flashMovie = getFlashMovieObject();" + LS +
                "        flashMovie.StopPlay();" + LS +
                "      }" + LS +
                "      function rewindFM() {" + LS +
                "        var flashMovie = getFlashMovieObject();" + LS +
                "        flashMovie.Rewind();" + LS +
                "      }" + LS +
                "      function setVariableFM(variableName, variableValue) {" + LS +
                "        var flashMovie = getFlashMovieObject();" + LS +
                "        flashMovie.SetVariable(decodeURIComponent(variableName), decodeURIComponent(variableValue));" + LS +
                "      }" + LS +
                "      function getVariableFM(variableName) {" + LS +
                "        var flashMovie = getFlashMovieObject();" + LS +
                "        try {" + LS +
                "          sendCommand('getVariableFM:' + flashMovie.GetVariable(decodeURIComponent(variableName)));" + LS +
                "        } catch(e) {" + LS +
                "          sendCommand('getVariableFM:');" + LS +
                "        }" + LS +
                "      }" + LS +
                "      //-->" + LS +
                "    </script>" + LS +
                "    <style type=\"text/css\">" + LS +
                "      html, object, embed, div, body { width: 100%; height: 100%; min-height: 100%; margin: 0; padding: 0; overflow: hidden; }" + LS +
                "      div { background-color: #FFFFFF; }" + LS +
                "    </style>" + LS +
                "  </head>" + LS +
                "  <body height=\"*\">" + LS +
                "    <script src=\"" + WebServer.getDefaultWebServer().getDynamicContentURL(JFlashPlayer.class.getName(), "js/" + instanceID) + "\"></script>" + LS +
                "  </body>" + LS +
                "</html>" + LS;
            return new ByteArrayInputStream(content.getBytes("UTF-8"));
          } catch(Exception e) {
            e.printStackTrace();
            return null;
          }
        }
      };
    }
    if("js".equals(type)) {
      final int instanceID = Integer.parseInt(resourcePath);
      JFlashPlayer player = (JFlashPlayer)Registry.getInstance().get(instanceID);
      if(player == null) {
        return null;
      }
      String url = player.url;
      // local files may have some security restrictions, so let's use our proxy.
      if(url.startsWith("file:/")) {
        url = WebServer.getDefaultWebServer().getResourcePathURL(url);
      }
      final FlashLoadingOptions loadingOptions = player.loadingOptions;
      player.loadingOptions = null;
      final String escapedURL = Utils.escapeXML(url);
      return new WebServerContent() {
        @Override
        public String getContentType() {
          return getDefaultMimeType(".js");
        }
        public InputStream getInputStream() {
          try {
            StringBuffer objectParameters = new StringBuffer();
            StringBuffer embedParameters = new StringBuffer();
            HashMap<String, String> parameters = new HashMap<String, String>(loadingOptions.getParameters());
            StringBuffer variablesSB = new StringBuffer();
            for(Entry<String, String> variable: loadingOptions.getVariables().entrySet()) {
              if(variablesSB.length() > 0) {
                variablesSB.append('&');
              }
              variablesSB.append(Utils.escapeXML(variable.getKey())).append('=').append(Utils.escapeXML(variable.getValue()));
            }
            if(variablesSB.length() > 0) {
              parameters.put("flashvars", variablesSB.toString());
            }
            parameters.remove("swliveconnect");
            parameters.remove("name");
            parameters.remove("src");
            parameters.remove("type");
            for(Entry<String, String> param: parameters.entrySet()) {
              String name = Utils.escapeXML(param.getKey());
              String value = Utils.escapeXML(param.getValue());
              embedParameters.append(' ').append(name).append("=\"").append(value).append("\"");
              objectParameters.append("window.document.write('  <param name=\"").append(name).append("\" value=\"").append(value).append("\">');" + LS);
            }
            String content =
                "<!--" + LS +
                "window.document.write('<object classid=\"clsid:D27CDB6E-AE6D-11cf-96B8-444553540000\" id=\"myFlashMovie\" codebase=\"http://fpdownload.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=9,0,0,0\">');" + LS +
                "window.document.write('  <param name=\"movie\" value=\"' + decodeURIComponent('" + escapedURL + "') + '\";\">');" + LS +
                objectParameters +
                "window.document.write('  <embed" + embedParameters + " swliveconnect=\"true\" name=\"myFlashMovie\" src=\"" + escapedURL + "\" type=\"application/x-shockwave-flash\" pluginspage=\"http://www.adobe.com/go/getflashplayer\">');" + LS +
                "window.document.write('  </embed>');" + LS +
                "window.document.write('</object>');" + LS +
                "//-->" + LS;
            return new ByteArrayInputStream(content.getBytes("UTF-8"));
          } catch(Exception e) {
            e.printStackTrace();
            return null;
          }
        }
      };
    }
    return null;
  }
  
  public void dispose() {
    webBrowser.dispose();
  }
  
  public boolean isDisposed() {
    return webBrowser.isDisposed();
  }

}
