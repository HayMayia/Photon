using System.Windows;
using LibVLCSharp.Shared;
using Photon.Core;

namespace Photon.Windows;
public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        try
        {
            var extractor = new ExtractorProcess(System.IO.Path.Combine(AppContext.BaseDirectory, "tools"));
            extractor.VerifyTools();
            new MediaTools(System.IO.Path.Combine(AppContext.BaseDirectory, "tools")).Verify();
            LibVLCSharp.Shared.Core.Initialize();
            var window = new MainWindow(extractor);
            if (e.Args.Contains("--self-test")) { window.Close(); Shutdown(0); return; }
            window.Show();
        }
        catch (Exception)
        {
            if (!e.Args.Contains("--self-test")) MessageBox.Show("Photon could not start. Reinstall the complete official Windows package. No security settings need to be disabled.", "Photon", MessageBoxButton.OK, MessageBoxImage.Error);
            Shutdown(1);
        }
    }
}
