using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace NostalifeLauncher {
static class Program {
    public const string Site="http://127.0.0.1:4182";
    public static readonly string Home=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"NostalifePleosContest");
    public const string Avd="Nostalife_Contest_5";
    public const string Component="kr.nostalife.pleos.contest.debug/kr.nostalife.companion.PleosActivity";
    [STAThread] static int Main(string[] args) {
        ServicePointManager.SecurityProtocol=SecurityProtocolType.Tls12;
        if(args.Contains("--self-test"))return Checks.Run(args.Last());
        Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
        using(var mutex=new Mutex(false,"Local\\NostalifePleosContest5")) {
            if(!mutex.WaitOne(0,false)){MessageBox.Show("Driving Lab 실행 창이 이미 열려 있습니다.","Driving Lab");return 0;}
            try{Directory.CreateDirectory(Home);Application.Run(new MainForm(args.Length==2&&args[0]=="--connect"?args[1]:null));}finally{mutex.ReleaseMutex();}
        }
        return 0;
    }
    public static string Code(string value){string clean=value.Trim().ToUpperInvariant().Replace("-","").Replace(" ","");if(!Regex.IsMatch(clean,"^[A-HJ-NP-Z2-9]{10}$"))throw new Exception("웹 화면의 연결 코드 10자리를 입력해 주세요. 예: K7Q4M-2P8WX");return clean;}
    public static string Quote(string value){
        if(value.IndexOfAny(new[]{'\r','\n','\0'})>=0)throw new ArgumentException("Invalid process argument");
        var result=new StringBuilder("\"");int slashes=0;
        foreach(char c in value){if(c=='\\'){slashes++;continue;}if(c=='\"')result.Append('\\',slashes*2+1).Append(c);else result.Append('\\',slashes).Append(c);slashes=0;}
        return result.Append('\\',slashes*2).Append('"').ToString();
    }
    public static string SafePath(string root,string relative){string full=Path.GetFullPath(Path.Combine(root,relative));if(!full.StartsWith(Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar)+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new InvalidDataException("설치 파일에 허용되지 않은 경로가 있습니다.");return full;}
    public static void Open(string url){
        if(url==Site){foreach(var directory in new[]{Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86)}){
            var chrome=Path.Combine(directory,"Google","Chrome","Application","chrome.exe");
            if(File.Exists(chrome)){Process.Start(new ProcessStartInfo(chrome,"--new-window "+Quote(url)){UseShellExecute=true});return;}
        }}
        Process.Start(new ProcessStartInfo(url){UseShellExecute=true});
    }
    public static async Task EnsureBridge(){
        using(var client=new HttpClient{Timeout=TimeSpan.FromSeconds(2)}){
            try{var reply=await client.GetStringAsync(Site+"/local/health");if(reply.Contains("5.1.0"))return;}catch{}
            var file=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"Nostalife-LocalBridge.exe");
            if(!File.Exists(file))throw new Exception("ZIP 전체를 풀어 주세요. Nostalife-LocalBridge.exe가 실행기 옆에 있어야 합니다.");
            Process.Start(new ProcessStartInfo(file){UseShellExecute=false,CreateNoWindow=true,WindowStyle=ProcessWindowStyle.Hidden,WorkingDirectory=Path.GetDirectoryName(file)});
            for(int i=0;i<12;i++){await Task.Delay(500);try{if((await client.GetStringAsync(Site+"/local/health")).Contains("5.1.0"))return;}catch{}}
            throw new Exception("로컬 주행 연결을 시작하지 못했습니다. 4182 포트 사용 여부를 확인해 주세요.");
        }
    }
    public static string Hash(string file,string algorithm="SHA256"){using(var input=File.OpenRead(file))using(var h=HashAlgorithm.Create(algorithm))return BitConverter.ToString(h.ComputeHash(input)).Replace("-","").ToLowerInvariant();}
    public static string ResourceText(string name){using(var s=Assembly.GetExecutingAssembly().GetManifestResourceStream(name))using(var r=new StreamReader(s,Encoding.UTF8))return r.ReadToEnd();}
}

sealed class MainForm:Form {
    readonly Color bg=Color.FromArgb(250,248,244),ink=Color.FromArgb(9,9,9),muted=Color.FromArgb(98,97,94),lime=Color.FromArgb(230,0,37);
    TextBox code;Label status,detail;ProgressBar bar;Button launch,cancel;CheckBox consent;CancellationTokenSource cancellation;bool busy;
    public MainForm(string initialCode=null){
        Text="Nostalife · Pleos Drive Studio 5.3";ClientSize=new Size(1020,690);MinimumSize=new Size(950,670);StartPosition=FormStartPosition.CenterScreen;BackColor=bg;ForeColor=ink;Font=new Font("맑은 고딕",11);AutoScaleMode=AutoScaleMode.Dpi;
        var panel=new TableLayoutPanel{Dock=DockStyle.Fill,Padding=new Padding(42,28,42,25),ColumnCount=1,RowCount=10};Controls.Add(panel);
        panel.RowStyles.Add(new RowStyle(SizeType.Absolute,43));panel.RowStyles.Add(new RowStyle(SizeType.Absolute,62));panel.RowStyles.Add(new RowStyle(SizeType.Absolute,65));panel.RowStyles.Add(new RowStyle(SizeType.Absolute,70));panel.RowStyles.Add(new RowStyle(SizeType.Absolute,48));panel.RowStyles.Add(new RowStyle(SizeType.Absolute,66));panel.RowStyles.Add(new RowStyle(SizeType.Absolute,52));panel.RowStyles.Add(new RowStyle(SizeType.Absolute,12));panel.RowStyles.Add(new RowStyle(SizeType.Percent,100));panel.RowStyles.Add(new RowStyle(SizeType.Absolute,40));
        AddLabel(panel,"N O S T A L I F E   /   P L E O S",16,lime,FontStyle.Bold);
        AddLabel(panel,"주행의 다음 장면을 여세요.",29,ink,FontStyle.Bold);
        AddLabel(panel,"별도 주행 창에서 운전하고 Pleos 앱에서 결과를 확인하세요.\nPleos API · 로컬 ACC · 3D 주행 창을 연결합니다.",13,muted,FontStyle.Regular);
        var entry=new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=3};entry.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,140));entry.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));entry.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,180));panel.Controls.Add(entry);
        entry.Controls.Add(new Label{Text="데이터 연결",Dock=DockStyle.Fill,TextAlign=ContentAlignment.MiddleLeft,ForeColor=muted});
        code=new TextBox{Dock=DockStyle.Top,Font=new Font("Consolas",26),BackColor=Color.White,ForeColor=ink,BorderStyle=BorderStyle.FixedSingle,MaxLength=16,Margin=new Padding(0,10,18,0),AccessibleName="웹 연결 코드"};entry.Controls.Add(code);
        code.Text="";code.ReadOnly=false;
        var web=MakeButton("별도 주행 창 ↗",false);web.Click+=async(s,e)=>{try{await Program.EnsureBridge();Program.Open(Program.Site);}catch(Exception error){status.Text=error.Message;}};entry.Controls.Add(web);
        var licenseRow=new FlowLayoutPanel{Dock=DockStyle.Fill,WrapContents=false};consent=new CheckBox{AutoSize=true,Text="Android·Pleos 개발 도구 약관에 동의합니다",ForeColor=muted,Margin=new Padding(0,10,16,0)};licenseRow.Controls.Add(consent);var terms=new LinkLabel{AutoSize=true,Text="약관 읽기",LinkColor=lime,Margin=new Padding(0,10,0,0)};terms.LinkClicked+=(s,e)=>ShowTerms();licenseRow.Controls.Add(terms);panel.Controls.Add(licenseRow);
        var row=new FlowLayoutPanel{Dock=DockStyle.Fill,WrapContents=false};launch=MakeButton("Pleos 설치 및 시작 →",true);launch.Width=340;launch.Click+=async(s,e)=>await Launch();row.Controls.Add(launch);cancel=MakeButton("설치 중단",false);cancel.Width=140;cancel.Enabled=false;cancel.Click+=(s,e)=>{if(cancellation!=null)cancellation.Cancel();};row.Controls.Add(cancel);panel.Controls.Add(row);
        status=new Label{Text="홈페이지에서 주행했다면 위에 웹 연결 코드를 입력하세요. PC 주행은 빈칸으로 시작합니다.",Dock=DockStyle.Fill,ForeColor=lime,Font=new Font("맑은 고딕",12,FontStyle.Bold),TextAlign=ContentAlignment.MiddleLeft};panel.Controls.Add(status);
        bar=new ProgressBar{Dock=DockStyle.Fill,Style=ProgressBarStyle.Continuous,Maximum=100};panel.Controls.Add(bar);
        detail=new Label{Text="처음 설치할 때 약 4.6 GB를 다운로드합니다.\nWindows 10/11 x64 · 메모리 16 GB 권장 · 여유 공간 40 GB\n가상화가 준비되지 않은 PC에는 필요한 설정을 안내합니다.",Dock=DockStyle.Fill,ForeColor=muted,Padding=new Padding(0,18,0,0),Font=new Font("맑은 고딕",11),AutoEllipsis=true};panel.Controls.Add(detail);
        var foot=new FlowLayoutPanel{Dock=DockStyle.Fill};var logs=new LinkLabel{Text="실행 기록 보기",AutoSize=true,LinkColor=muted,Margin=new Padding(0,10,25,0)};logs.LinkClicked+=(s,e)=>Program.Open(Program.Home);foot.Controls.Add(logs);foot.Controls.Add(new Label{Text="개발용 시연 · 가상 차량 분석",AutoSize=true,ForeColor=muted,Margin=new Padding(0,10,0,0)});panel.Controls.Add(foot);
        if(Installer.ExistingSdk()!=null){consent.Visible=false;terms.Text="공식 도구 약관";launch.Text="Pleos 시작 →";detail.Text="이 PC에 설치된 공식 Android·Pleos 도구를 사용합니다.\n수정한 Nostalife Drive APK를 별도 앱으로 설치합니다.\n이 PC의 주행 창과 ACC 입력을 자동으로 연결합니다.";}
        FormClosing+=(s,e)=>{if(busy){e.Cancel=true;if(cancellation!=null)cancellation.Cancel();status.Text="작업을 중단하고 있습니다. 잠시 후 닫아 주세요.";}};
        if(initialCode!=null){code.Text=initialCode;if(Installer.ExistingSdk()!=null)Shown+=async(s,e)=>await Launch();}
    }
    void AddLabel(TableLayoutPanel p,string text,float size,Color color,FontStyle style){p.Controls.Add(new Label{Text=text,Dock=DockStyle.Fill,Font=new Font("맑은 고딕",size,style),ForeColor=color,TextAlign=ContentAlignment.MiddleLeft});}
    Button MakeButton(string text,bool primary){var b=new Button{Text=text,Height=52,Width=170,Margin=new Padding(0,6,14,0),FlatStyle=FlatStyle.Flat,BackColor=primary?lime:ink,ForeColor=Color.White,Font=new Font("맑은 고딕",12,FontStyle.Bold),Cursor=Cursors.Hand};b.FlatAppearance.BorderSize=0;return b;}
    void ShowTerms(){using(var dialog=new Form{Text="공식 개발 도구 이용 약관",Size=new Size(900,680),StartPosition=FormStartPosition.CenterParent}){var tabs=new TabControl{Dock=DockStyle.Fill};foreach(var name in new[]{"android-license.txt","pleos-license.txt"}){var page=new TabPage(name.StartsWith("android")?"Android SDK":"Pleos Connect SDK");page.Controls.Add(new TextBox{Text=Program.ResourceText(name),Multiline=true,ReadOnly=true,ScrollBars=ScrollBars.Vertical,Dock=DockStyle.Fill,Font=new Font("맑은 고딕",10)});tabs.TabPages.Add(page);}dialog.Controls.Add(tabs);dialog.ShowDialog(this);}}
    async Task Launch(){
        if(busy)return;string pair="";
        try{if(!String.IsNullOrWhiteSpace(code.Text))pair=Program.Code(code.Text);}catch(Exception error){status.Text=error.Message;return;}
        if(!Environment.Is64BitOperatingSystem){status.Text="Windows x64 PC가 필요합니다.";return;}
        string sdk=Installer.ExistingSdk();if(sdk==null&&!consent.Checked){status.Text="처음 설치하려면 공식 개발 도구 약관을 읽고 동의해 주세요.";return;}
        busy=true;launch.Enabled=false;code.Enabled=false;cancel.Enabled=true;cancellation=new CancellationTokenSource();
        var progress=new Progress<ProgressState>(p=>{status.Text=p.Title;detail.Text=p.Detail;bar.Value=Math.Max(0,Math.Min(100,p.Percent));});
        try{
            await Program.EnsureBridge();
            await Installer.Start(sdk,pair,progress,cancellation.Token);
            status.Text="Pleos 앱을 열었습니다.";detail.Text="별도 주행 창에서 ‘분석하고 앱으로 보내기’를 누르면 자동으로 도착합니다.\n이 실행 창은 닫아도 됩니다. Pleos 창은 계속 실행됩니다.";bar.Value=100;
        }catch(OperationCanceledException){status.Text="중단했습니다. 다시 시작하면 다운로드를 이어받습니다.";}
        catch(Exception e){status.Text="실행을 완료하지 못했습니다.";detail.Text=e.Message;File.AppendAllText(Path.Combine(Program.Home,"launcher.log"),DateTime.Now.ToString("s")+" "+e+Environment.NewLine);}
        finally{busy=false;launch.Enabled=true;code.Enabled=true;cancel.Enabled=false;launch.Text="Pleos 다시 열기 →";cancellation.Dispose();cancellation=null;}
    }
}
sealed class ProgressState {public string Title,Detail;public int Percent;public ProgressState(string title,string detail,int percent){Title=title;Detail=detail;Percent=percent;}}
static class Installer {
    static readonly string ImageRelative=Path.Combine("system-images","android-34","pleos_car_emulator","x86_64");
    public static string ExistingSdk(){foreach(var sdk in new[]{Environment.GetEnvironmentVariable("ANDROID_HOME"),Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Android","Sdk"),Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"NostalifePleos","sdk"),Path.Combine(Program.Home,"sdk")})if(!String.IsNullOrEmpty(sdk)&&File.Exists(Path.Combine(sdk,"emulator","emulator.exe"))&&File.Exists(Path.Combine(sdk,"platform-tools","adb.exe"))&&File.Exists(Path.Combine(sdk,ImageRelative,"system.img")))return sdk;return null;}
    static void Report(IProgress<ProgressState> p,string title,string detail,int percent){p.Report(new ProgressState(title,detail,percent));}
    static async Task<string> Run(string file,IEnumerable<string> args,int timeout,CancellationToken ct,Dictionary<string,string> env=null,bool logOutput=true){
        var start=new ProcessStartInfo(file,String.Join(" ",args.Select(Program.Quote))){UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true,StandardOutputEncoding=Encoding.UTF8,StandardErrorEncoding=Encoding.UTF8};
        if(env!=null)foreach(var kv in env)start.EnvironmentVariables[kv.Key]=kv.Value;
        using(var proc=Process.Start(start)){
            var output=proc.StandardOutput.ReadToEndAsync();var error=proc.StandardError.ReadToEndAsync();var watch=Stopwatch.StartNew();
            while(!proc.HasExited){if(ct.IsCancellationRequested||watch.ElapsedMilliseconds>timeout){try{proc.Kill();}catch{}ct.ThrowIfCancellationRequested();throw new TimeoutException("기기의 응답이 늦습니다. 잠시 기다린 뒤 다시 실행해 주세요.");}await Task.Delay(250,ct);}
            var text=await output+await error;File.AppendAllText(Path.Combine(Program.Home,"launcher.log"),DateTime.Now.ToString("s")+" "+Path.GetFileName(file)+" exit="+proc.ExitCode+Environment.NewLine+(logOutput?text:"[diagnostic output omitted]")+Environment.NewLine);
            if(proc.ExitCode!=0)throw new Exception(text.Length>1600?text.Substring(0,1600):text);return text;
        }
    }
    public static async Task Start(string sdk,string code,IProgress<ProgressState> progress,CancellationToken ct){
        Directory.CreateDirectory(Program.Home);
        if(sdk==null){
            sdk=Path.Combine(Program.Home,"sdk");Directory.CreateDirectory(sdk);
            if(new DriveInfo(Path.GetPathRoot(sdk)).AvailableFreeSpace<40L*1024*1024*1024)throw new Exception("처음 설치하려면 C 드라이브에 약 40 GB의 여유 공간이 필요합니다. 공간을 확보한 뒤 다시 시작해 주세요.");
            await Package(sdk,"emulator","https://dl.google.com/android/repository/emulator-windows_x64-16740729.zip","4d7268aa7787c640ad892844e6c9a5f159e91ad3",455344678,progress,ct);
            await Package(sdk,"platform-tools","https://dl.google.com/android/repository/platform-tools_r37.0.1-win.zip","e03e78b1d80b396f1c3358e31251cb31740e1110",8044989,progress,ct);
            await Package(sdk,"pleos","https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp_car_emulator.zip","b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa",4087751964,progress,ct);
        }
        string emulator=Path.Combine(sdk,"emulator","emulator.exe"),adb=Path.Combine(sdk,"platform-tools","adb.exe");
        Report(progress,"PC 실행 환경을 확인하고 있습니다.","Windows 가상화 지원을 확인합니다.",5);
        try{await Run(emulator,new[]{"-accel-check"},20000,ct);}catch(OperationCanceledException){throw;}catch(Exception){throw new Exception("이 PC의 Android 실행용 가상화가 준비되지 않았습니다. Windows 기능에서 ‘Windows 하이퍼바이저 플랫폼’을 켜고 재부팅하세요. BIOS의 Intel VT-x / AMD SVM도 켜져 있어야 합니다. 회사 PC에서는 관리자에게 요청해 주세요. 설정을 자동으로 변경하지 않습니다.");}
        string serial=null;var devices=await Run(adb,new[]{"devices"},20000,ct);
        foreach(Match m in Regex.Matches(devices,@"(emulator-\d+)\s+device")){try{var name=await Run(adb,new[]{"-s",m.Groups[1].Value,"emu","avd","name"},10000,ct);if(name.Contains(Program.Avd)||name.Contains("Nostalife_Pleos_2_1")){serial=m.Groups[1].Value;break;}}catch{ct.ThrowIfCancellationRequested();}}
        if(serial==null){
            string avdHome=Path.Combine(Program.Home,"avd");Directory.CreateDirectory(avdHome);WriteAvd(avdHome,sdk);
            int port=5560;while(devices.Contains("emulator-"+port)&&port<5680)port+=2;serial="emulator-"+port;
            var args=new[]{"-avd",Program.Avd,"-port",port.ToString(),"-no-snapshot","-gpu","software","-no-boot-anim"};
            var start=new ProcessStartInfo(emulator,String.Join(" ",args.Select(Program.Quote))){UseShellExecute=false,CreateNoWindow=true,WorkingDirectory=Program.Home};start.EnvironmentVariables["ANDROID_HOME"]=sdk;start.EnvironmentVariables["ANDROID_SDK_ROOT"]=sdk;start.EnvironmentVariables["ANDROID_AVD_HOME"]=avdHome;Process.Start(start);
        }
        Report(progress,"실제 Pleos 화면을 시작합니다.","첫 실행은 몇 분 걸릴 수 있습니다. 차량 홈이 나타날 때까지 기다려 주세요.",25);
        var deadline=DateTime.UtcNow.AddMinutes(7);bool booted=false;
        while(DateTime.UtcNow<deadline){ct.ThrowIfCancellationRequested();try{var boot=await Run(adb,new[]{"-s",serial,"shell","getprop","sys.boot_completed"},10000,ct);if(boot.Trim()=="1"){booted=true;break;}}catch{ct.ThrowIfCancellationRequested();}await Task.Delay(2500,ct);}
        if(!booted)throw new Exception("Pleos 시작 시간이 초과됐습니다. 다른 에뮬레이터를 닫고 메모리 여유를 확보한 후 다시 시작해 주세요.");
        Report(progress,"Driving Lab 앱을 적용합니다.","새 화면과 자동 결과 연결을 준비합니다.",65);
        string apk=Path.Combine(Program.Home,"Nostalife-Pleos-5.3.0.apk");await Task.Run(()=>ExtractApk(apk),ct);
        await Run(adb,new[]{"-s",serial,"install","-r","-g",apk},180000,ct);
        await Run(adb,new[]{"-s",serial,"reverse","tcp:4182","tcp:4182"},20000,ct);
        var user=(await Run(adb,new[]{"-s",serial,"shell","am","get-current-user"},10000,ct)).Trim();
        if(!Regex.IsMatch(user,"^[0-9]+$"))throw new Exception("Pleos 운전자 화면이 아직 준비되지 않았습니다. 잠시 후 다시 실행해 주세요.");
        int stable=0,missed=0,restarts=0;bool restart=true;var visibleDeadline=DateTime.UtcNow.AddMinutes(3);
        while(stable<3&&DateTime.UtcNow<visibleDeadline){
            if(restart){
                Report(progress,restarts==0?"차량 홈에서 Driving Lab을 엽니다.":"앱 화면을 다시 배치하고 있습니다.","앱이 차량 홈 앞에 표시되는지 확인합니다. 연결 정보는 유지됩니다.",85);
                await Run(adb,new[]{"-s",serial,"shell","am","force-stop","--user",user,"kr.nostalife.pleos.contest.debug"},20000,ct);
                var startArgs=new List<string>{"-s",serial,"shell","am","start","-W","--user",user,"-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER","-f","0x10200000","-n",Program.Component};
                if(!String.IsNullOrEmpty(code)){startArgs.AddRange(new[]{"--es","nostalife_pair_code",code});}
                else startArgs.AddRange(new[]{"--ez","nostalife_local","true"});
                await Run(adb,startArgs,90000,ct,null,false);
                restarts++;restart=false;missed=0;
            }
            await Task.Delay(5000,ct);
            bool visible=false;
            try{
                var ui=await Run(adb,new[]{"-s",serial,"shell","run-as","kr.nostalife.pleos.contest.debug","--user",user,"cat","files/ui-status.json"},10000,ct);
                var windows=await Run(adb,new[]{"-s",serial,"shell","dumpsys","window","windows"},20000,ct,null,false);
                visible=ui.Contains("\"visible\":true")&&HasVisiblePleosWindow(windows);
            }catch{ct.ThrowIfCancellationRequested();}
            stable=visible?stable+1:0;missed=visible?0:missed+1;
            File.AppendAllText(Path.Combine(Program.Home,"launcher.log"),DateTime.Now.ToString("s")+" app_panel_visible="+visible+" stable="+stable+" launch_attempt="+restarts+Environment.NewLine);
            if(missed>=2&&restarts<4)restart=true;
        }
        if(stable<3)throw new Exception("앱이 차량 화면 앞에 표시되는 것을 확인하지 못했습니다. ‘Pleos 다시 열기’를 누르면 앱만 다시 시작합니다. 연결 코드와 주행 결과는 유지됩니다.");
        File.WriteAllLines(Path.Combine(Program.Home,"installation-proof.txt"),new[]{
            "verified_at_utc="+DateTime.UtcNow.ToString("o"),"component="+Program.Component,
            "apk_sha256="+Program.Hash(apk),"adb_install=success","adb_reverse_4182=success",
            "pleos_foreground_panel_visible=true","consecutive_visible_observations="+stable,
            "scope=official development emulator; production vehicle not verified"},new UTF8Encoding(false));
        Report(progress,"주행 결과 연결 완료","별도 주행 창에서 분석하면 Pleos 앱에 도착합니다.",100);
    }
    public static bool HasVisiblePleosWindow(string dump){
        foreach(var block in Regex.Split(dump,@"(?m)(?=^[ \t]*Window #\d+ )")){
            string header=block.TrimStart().Split('\n')[0];
            if(!header.Contains(Program.Component))continue;
            if(!block.Contains("mDisplayId=0 ")||!block.Contains("mViewVisibility=0x0")||!block.Contains("mObscured=false")||!block.Contains("mHasSurface=true")||!block.Contains("mDrawState=HAS_DRAWN")||!block.Contains("isOnScreen=true")||!block.Contains("isVisible=true"))continue;
            var frame=Regex.Match(block,@"\bframe=\[(\d+),(\d+)\]\[(\d+),(\d+)\]");
            if(!frame.Success)continue;
            int left=Int32.Parse(frame.Groups[1].Value),top=Int32.Parse(frame.Groups[2].Value),right=Int32.Parse(frame.Groups[3].Value),bottom=Int32.Parse(frame.Groups[4].Value);
            // The pinned Pleos 2.1 image places apps right of the driving panel and below its status bar.
            // A task left at (0,0) during boot can report visible while its surface remains behind Home.
            if(left>0&&top>0&&right-left>=640&&bottom-top>=480&&right<=2560&&bottom<=1440)return true;
        }
        return false;
    }
    public static void ExtractApk(string target){
        string expected=Program.ResourceText("apk.sha256").Trim();if(File.Exists(target)&&Program.Hash(target)==expected)return;
        string temp=target+".part";using(var s=Assembly.GetExecutingAssembly().GetManifestResourceStream("app.apk.gz"))using(var gzip=new GZipStream(s,CompressionMode.Decompress))using(var output=File.Create(temp))gzip.CopyTo(output);
        if(Program.Hash(temp)!=expected){File.Delete(temp);throw new InvalidDataException("앱 파일 검증에 실패했습니다. 실행 파일을 다시 받아 주세요.");}
        if(File.Exists(target))File.Delete(target);File.Move(temp,target);
    }
    static async Task Package(string sdk,string name,string url,string checksum,long size,IProgress<ProgressState> progress,CancellationToken ct){
        string installed=name=="pleos"?Path.Combine(sdk,ImageRelative):Path.Combine(sdk,name);
        string marker=Path.Combine(installed,".nostalife-package");if(File.Exists(marker)&&File.ReadAllText(marker)==checksum)return;
        string downloads=Path.Combine(Program.Home,"downloads");Directory.CreateDirectory(downloads);string zip=Path.Combine(downloads,name+".zip"),part=zip+".part";
        if(!File.Exists(zip)||new FileInfo(zip).Length!=size||await Task.Run(()=>Program.Hash(zip,"SHA1"),ct)!=checksum){
            long offset=File.Exists(part)?new FileInfo(part).Length:0;if(offset>=size){
                if(offset==size && await Task.Run(()=>Program.Hash(part,"SHA1"),ct)==checksum){if(File.Exists(zip))File.Delete(zip);File.Move(part,zip);await Package(sdk,name,url,checksum,size,progress,ct);return;}
                File.Delete(part);offset=0;
            }
            using(var client=new HttpClient{Timeout=TimeSpan.FromHours(2)})using(var request=new HttpRequestMessage(HttpMethod.Get,url)){
                if(offset>0)request.Headers.Range=new System.Net.Http.Headers.RangeHeaderValue(offset,null);
                using(var response=await client.SendAsync(request,HttpCompletionOption.ResponseHeadersRead,ct)){
                    response.EnsureSuccessStatusCode();if(response.StatusCode!=HttpStatusCode.PartialContent)offset=0;
                    using(var input=await response.Content.ReadAsStreamAsync())using(var output=new FileStream(part,offset==0?FileMode.Create:FileMode.Append,FileAccess.Write,FileShare.None,1024*1024,true)){
                        var buffer=new byte[1024*1024];int n;long total=offset;var watch=Stopwatch.StartNew();
                        while((n=await input.ReadAsync(buffer,0,buffer.Length,ct))>0){total+=n;if(total>size)throw new InvalidDataException("공식 설치 파일의 크기가 예상과 다릅니다.");await output.WriteAsync(buffer,0,n,ct);if(watch.ElapsedMilliseconds>300){Report(progress,name=="pleos"?"Pleos 공식 이미지 다운로드 중":"Android 실행 도구 다운로드 중",String.Format("{0:N0} / {1:N0} MB · 연결이 끊기면 다음에 이어받습니다.",total/1048576d,size/1048576d),(int)(total*100/size));watch.Restart();}}
                    }
                }
            }
            Report(progress,"다운로드 파일을 검증합니다.","공식 배포 목록의 체크섬과 비교합니다.",100);
            if(new FileInfo(part).Length!=size||await Task.Run(()=>Program.Hash(part,"SHA1"),ct)!=checksum){File.Delete(part);throw new InvalidDataException("설치 파일 검증에 실패했습니다. 다시 실행하면 새로 받습니다.");}
            if(File.Exists(zip))File.Delete(zip);File.Move(part,zip);
        }
        Report(progress,name=="pleos"?"Pleos 이미지 설치 중":"Android 실행 도구 설치 중","큰 파일을 풀고 있습니다. 이 단계는 몇 분 걸릴 수 있습니다.",0);
        string staging=Path.Combine(sdk,".install-"+name);Directory.CreateDirectory(staging);
        await Task.Run(()=>ExtractZip(zip,staging,progress,ct),ct);
        string source=name=="pleos"?Directory.GetFiles(staging,"system.img",SearchOption.AllDirectories).Select(Path.GetDirectoryName).Single():Path.Combine(staging,name);
        if(Directory.Exists(installed))Directory.Move(installed,installed+".previous-"+DateTime.UtcNow.Ticks);
        Directory.CreateDirectory(Path.GetDirectoryName(installed));Directory.Move(source,installed);File.WriteAllText(marker,checksum);
    }
    public static void ExtractZip(string zip,string target,IProgress<ProgressState> progress,CancellationToken ct){
        using(var archive=ZipFile.OpenRead(zip)){
            int i=0;foreach(var entry in archive.Entries){ct.ThrowIfCancellationRequested();if(((entry.ExternalAttributes>>16)&0xF000)==0xA000)throw new InvalidDataException("Unsupported archive link");string file=Program.SafePath(target,entry.FullName.Replace('/',Path.DirectorySeparatorChar));if(entry.FullName.EndsWith("/")){Directory.CreateDirectory(file);continue;}Directory.CreateDirectory(Path.GetDirectoryName(file));using(var input=entry.Open())using(var output=File.Create(file)){var buffer=new byte[1024*1024];int n;while((n=input.Read(buffer,0,buffer.Length))>0){ct.ThrowIfCancellationRequested();output.Write(buffer,0,n);}}i++;Report(progress,"설치 파일을 풀고 있습니다.","공식 구성요소 설치 · "+i+" / "+archive.Entries.Count,(int)(100d*i/archive.Entries.Count));}
        }
    }
    public static void WriteAvd(string avdHome,string sdk){
        string folder=Path.Combine(avdHome,Program.Avd+".avd");Directory.CreateDirectory(folder);
        var rows=new[]{"AvdId="+Program.Avd,"avd.ini.displayname=Nostalife Pleos","avd.ini.encoding=UTF-8","abi.type=x86_64","image.sysdir.1="+Path.Combine(sdk,ImageRelative)+Path.DirectorySeparatorChar,"tag.id=android-automotive","tag.display=Pleos Connect v2.1.0 APIs","target=android-34","hw.device.name=Pleos_Connect_v2_0","hw.device.manufacturer=42dot","hw.cpu.arch=x86_64","hw.cpu.ncore=4","hw.ramSize=4096","vm.heapSize=256","hw.lcd.width=2560","hw.lcd.height=1440","hw.lcd.density=160","hw.gpu.enabled=yes","hw.gpu.mode=software","hw.keyboard=yes","hw.mainKeys=no","hw.gps=yes","hw.accelerometer=no","hw.sensors.orientation=no","hw.battery=no","disk.dataPartition.size=8G","fastboot.forceColdBoot=yes","showDeviceFrame=no","skin.dynamic=yes","hw.display1.width=720","hw.display1.height=1280","hw.display1.density=213","hw.display1.flag=1739","hw.multi_display_window=no","hw.hotplug_multi_display=no"};
        File.WriteAllLines(Path.Combine(folder,"config.ini"),rows,new UTF8Encoding(false));File.WriteAllText(Path.Combine(avdHome,Program.Avd+".ini"),"avd.ini.encoding=UTF-8\npath="+folder+"\ntarget=android-34\n",new UTF8Encoding(false));
    }
}
static class Checks {
    public static int Run(string report){var passed=new List<string>();try{
        if(Program.Code("k7q4m-2p8wx")!="K7Q4M2P8WX")throw new Exception("code normalization");passed.Add("code normalization");
        bool rejected=false;try{Program.Code("A;cmd.exe");}catch{rejected=true;}if(!rejected)throw new Exception("code injection");passed.Add("reject malformed code");
        string area=Path.Combine(Path.GetDirectoryName(Path.GetFullPath(report)),"launcher-selftest");Directory.CreateDirectory(area);
        rejected=false;try{Program.SafePath(area,"../escape");}catch{rejected=true;}if(!rejected)throw new Exception("zip traversal");passed.Add("reject archive traversal");
        string apk=Path.Combine(area,"app.apk");Installer.ExtractApk(apk);if(Program.Hash(apk)!=Program.ResourceText("apk.sha256").Trim())throw new Exception("apk checksum");passed.Add("embedded APK decompression and SHA256");
        Installer.WriteAvd(Path.Combine(area,"avd"),Path.Combine(area,"sdk"));if(!File.ReadAllText(Path.Combine(area,"avd",Program.Avd+".avd","config.ini")).Contains("hw.lcd.width=2560"))throw new Exception("AVD config");passed.Add("official image AVD configuration");
        if(!Program.ResourceText("pleos-license.txt").Contains("License Agreement"))throw new Exception("license missing");passed.Add("official license resources");
        string window="  Window #13 Window{e99809b u10 "+Program.Component+"}:\n    mDisplayId=0 rootTaskId=1000035\n    mViewVisibility=0x0 mObscured=false mHasSurface=true\n    Frames: frame=[853,90][2535,1300]\n    mDrawState=HAS_DRAWN isOnScreen=true isVisible=true\n";
        if(!Installer.HasVisiblePleosWindow("WINDOW MANAGER WINDOWS\n\n"+window+"\n  Window #14 Window{other}:\n    frame=[0,0][2560,1440]\n"))throw new Exception("visible app panel");passed.Add("accept app in visible Pleos panel");
        foreach(var bad in new[]{window.Replace("frame=[853,90][2535,1300]","frame=[0,0][1682,1350]"),window.Replace("isOnScreen=true","isOnScreen=false"),window.Replace("mDisplayId=0 ","mDisplayId=2 "),window.Replace(Program.Component,"ai.umos.maps/.MainActivity")})if(Installer.HasVisiblePleosWindow(bad))throw new Exception("hidden or misplaced task accepted");passed.Add("reject boot-time misplaced task and hidden or unrelated windows");
        File.WriteAllText(report,"PASS "+passed.Count+Environment.NewLine+String.Join(Environment.NewLine,passed));return 0;
    }catch(Exception e){File.WriteAllText(report,"FAIL "+e);return 1;}}
}
}
