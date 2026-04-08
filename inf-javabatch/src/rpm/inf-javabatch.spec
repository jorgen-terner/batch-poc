Name:           inf-javabatch
Version:        @VERSION@
Release:        @RELEASE@%{?dist}
Summary:        Quarkus batch hook worker
License:        Proprietary
BuildArch:      noarch
Source0:        inf-javabatch-@VERSION@

%description
Quarkus worker that invokes START and STOP hook URLs for suspended Kubernetes Jobs.

%prep
cp -a %{SOURCE0} %{_builddir}/%{name}-%{version}

%build

%install
mkdir -p %{buildroot}
cp -a %{_builddir}/%{name}-%{version}/opt %{buildroot}/

%files
/opt/inf-javabatch

%changelog
* Tue Apr 08 2026 GitHub Copilot <copilot@example.com> - @VERSION@-@RELEASE@
- Initial package