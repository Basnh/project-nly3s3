package com.example.sshcontrol.sshcontrol.controller;

import com.example.sshcontrol.model.User;
import com.example.sshcontrol.model.ServerInfo;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;

@Controller
public class AuthController {

    // Danh sách người dùng giả lập (không dùng CSDL)
    private static List<User> users = new ArrayList<>();

    static {
        List<ServerInfo> servers = new ArrayList<>();
        servers.add(new ServerInfo("Server 1", "192.168.1.10", "ubuntu", "123456"));
        servers.add(new ServerInfo("Server 2", "192.168.1.20", "ubuntu", "123456"));
        users.add(new User("admin", "admin", servers));
    }

    // Hiển thị form đăng nhập
    @GetMapping("/login")
    public String showLogin(Model model) {
        model.addAttribute("user", new User());
        return "login";
    }

    // Xử lý đăng nhập
    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {
        Optional<User> found = users.stream()
            .filter(u -> u.getUsername().equals(username) && u.getPassword().equals(password))
            .findFirst();

        if (found.isPresent()) {
            session.setAttribute("user", found.get());
            return "redirect:/";
        } else {
            model.addAttribute("error", "Sai tên đăng nhập hoặc mật khẩu");
            model.addAttribute("user", new User());
            return "login";
        }
    }

    // Đăng xuất
    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    // Hiển thị form đăng ký
    @GetMapping("/register")
    public String showRegister(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    // Xử lý đăng ký
    @PostMapping("/register")
    public String register(@ModelAttribute User user, Model model) {
        boolean exists = users.stream().anyMatch(u -> u.getUsername().equals(user.getUsername()));
        if (exists) {
            model.addAttribute("error", "Tên đăng nhập đã tồn tại");
            return "register";
        }
        user.setServers(new ArrayList<>()); // Tạo danh sách server rỗng cho người dùng mới
        users.add(user);
        return "redirect:/login";
    }

    // Danh sách máy chủ
    @GetMapping("/server-list")
    public String serverList(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
    
    model.addAttribute("servers", user.getServers());
    return "server-list";
    }

    // Form đăng nhập SSH vào máy chủ
    @GetMapping("/server-login")
    public String showServerLogin(@RequestParam String ip, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";
        ServerInfo server = user.getServers().stream()
            .filter(s -> s.getIp().equals(ip))
            .findFirst().orElse(null);
        if (server == null) return "redirect:/server-list";
        model.addAttribute("server", server);
        return "server-login";
    }

    // Xử lý đăng nhập SSH vào máy chủ
    @PostMapping("/server-login")
    public String serverLogin(@RequestParam String ip,
                              @RequestParam String sshUsername,
                              @RequestParam String sshPassword,
                              HttpSession session) {
        session.setAttribute("host", ip);
        session.setAttribute("username", sshUsername);
        session.setAttribute("password", sshPassword);
        return "redirect:/dashboard";
    }

    // Hiển thị form thêm máy chủ
    @GetMapping("/add-server")
    public String showAddServer(Model model) {
        model.addAttribute("server", new ServerInfo());
        return "add-server";
    }

    // Xử lý thêm máy chủ
    @PostMapping("/add-server")
    public String addServer(@ModelAttribute ServerInfo server, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";
        if (user.getServers() == null) user.setServers(new ArrayList<>());

        // Kiểm tra trùng IP + SSH Username + Password
        boolean exists = user.getServers().stream().anyMatch(s ->
            s.getIp().equalsIgnoreCase(server.getIp()) &&
            s.getSshUsername().equalsIgnoreCase(server.getSshUsername()) &&
            s.getSshPassword().equals(server.getSshPassword())
        );
        if (exists) {
            model.addAttribute("server", server);
            model.addAttribute("error", "Máy chủ với IP, SSH Username và Password này đã tồn tại!");
            return "add-server";
        }

        user.getServers().add(server);
        return "redirect:/dashboard";
    }

    // Xóa máy chủ
    @PostMapping("/delete-server")
    public String deleteServer(@RequestParam String ip, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        
        if (user.getServers() != null) {
            boolean removed = user.getServers().removeIf(server -> server.getIp().equals(ip));
            
            if (removed) {
                // Cập nhật lại user trong session
                session.setAttribute("user", user);
                
                // Cập nhật trong danh sách users toàn cục
                for (User u : users) {
                    if (u.getUsername().equals(user.getUsername())) {
                        u.setServers(user.getServers());
                        break;
                    }
                }
                
                redirectAttributes.addFlashAttribute("message", "Xóa máy chủ thành công!");
            } else {
                redirectAttributes.addFlashAttribute("error", "Không tìm thấy máy chủ để xóa!");
            }
        }
        
        return "redirect:/server-list";
    }

    // Thêm method GET này vào AuthController (giữ nguyên method POST hiện tại)
    @GetMapping("/delete-server")
    public String deleteServerGet(@RequestParam String ip, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        
        if (user.getServers() != null) {
            boolean removed = user.getServers().removeIf(server -> server.getIp().equals(ip));
            
            if (removed) {
                // Cập nhật lại user trong session
                session.setAttribute("user", user);
                
                // Cập nhật trong danh sách users toàn cục
                for (User u : users) {
                    if (u.getUsername().equals(user.getUsername())) {
                        u.setServers(user.getServers());
                        break;
                    }
                }
                
                redirectAttributes.addFlashAttribute("message", "Xóa máy chủ thành công!");
            } else {
                redirectAttributes.addFlashAttribute("error", "Không tìm thấy máy chủ để xóa!");
            }
        }
        
        return "redirect:/server-list";
    }

    // Hiển thị trang dashboard
    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        
        // Kiểm tra trạng thái máy chủ khi load dashboard
        if (user.getServers() != null) {
            for (ServerInfo server : user.getServers()) {
                boolean isOnline = checkServerStatus(server.getIp());
                server.setOnline(isOnline);
            }
        }
        
        model.addAttribute("user", user);
        return "dashboard";
    }


    private boolean checkServerStatus(String ip) {
        try {
            // Ping test
            InetAddress address = InetAddress.getByName(ip);
            boolean reachable = address.isReachable(3000); // 3 seconds timeout
            
            if (!reachable) {
                return false;
            }
            
            // SSH port test (port 22)
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, 22), 3000);
                return true;
            } catch (IOException e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

}
