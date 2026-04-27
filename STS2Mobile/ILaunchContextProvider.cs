namespace STS2Mobile;

public interface ILaunchContextProvider
{
    bool TryLoad(out LaunchContext context, out string source);
}
